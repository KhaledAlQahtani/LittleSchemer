package som.interpreter.nodes.dispatch;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchFalseCheckNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchTrueCheckNode;
import som.vm.NotYetImplementedException;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public final class UninitializedDispatchNode extends AbstractDispatchWithLookupNode {

  public UninitializedDispatchNode(final SSymbol selector) {
    super(selector);
  }

  private AbstractDispatchNode specialize(final Object[] arguments) {
    transferToInterpreterAndInvalidate("Initialize a dispatch node.");
    Object rcvr = arguments[0];

    assert rcvr != null;

    // Determine position in dispatch node chain, i.e., size of inline cache
    Node i = this;
    int chainDepth = 0;
    while (i.getParent() instanceof AbstractDispatchNode) {
      i = i.getParent();
      chainDepth++;
    }
    GenericMessageSendNode sendNode = (GenericMessageSendNode) i.getParent();

    if (chainDepth < INLINE_CACHE_SIZE) {
      SClass rcvrClass = Types.getClassOf(rcvr);
      SInvokable method = rcvrClass.lookupInvokable(selector);
      CallTarget callTarget;
      if (method != null) {
        callTarget = method.getCallTarget();
      } else {
        callTarget = null;
      }

      UninitializedDispatchNode newChainEnd = new UninitializedDispatchNode(selector);

      if (rcvr instanceof SObject) {
        AbstractCachedDispatchNode node;
        if (method != null) {
          node = new CachedDispatchSObjectCheckNode(
              rcvrClass, callTarget, newChainEnd);
        } else {
          node = new CachedDnuSObjectCheckNode(rcvrClass, selector, newChainEnd);
        }

        if ((getParent() instanceof CachedDispatchSObjectCheckNode)) {
          return replace(node);
        } else {
          SObjectCheckDispatchNode checkNode = new SObjectCheckDispatchNode(node,
              new UninitializedDispatchNode(selector));
          return replace(checkNode);
        }
      } else {
        if (method == null) {
          throw new NotYetImplementedException();
        }
        // the simple checks are prepended

        AbstractCachedDispatchNode node;
        AbstractDispatchNode next = sendNode.getDispatchListHead();

        if (rcvr == Boolean.TRUE) {
          node = new CachedDispatchTrueCheckNode(callTarget, next);
        } else if (rcvr == Boolean.FALSE) {
          node = new CachedDispatchFalseCheckNode(callTarget, next);
        } else {
          node = new CachedDispatchSimpleCheckNode(
                rcvr.getClass(), callTarget, next);
        }
        sendNode.adoptNewDispatchListHead(node);
        return node;
      }
    }

    // the chain is longer than the maximum defined by INLINE_CACHE_SIZE and
    // thus, this callsite is considered to be megaprophic, and we generalize
    // it.
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector);
    sendNode.replaceDispatchListHead(genericReplacement);
    return genericReplacement;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return specialize(arguments).
        executeDispatch(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 0;
  }

}
