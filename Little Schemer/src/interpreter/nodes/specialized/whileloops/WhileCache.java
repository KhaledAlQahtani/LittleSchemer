package som.interpreter.nodes.specialized.whileloops;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.vm.constants.Globals;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public final class WhileCache {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractWhileDispatch create(final boolean predicateBool) {
    return new UninitializedDispatch(predicateBool, 0);
  }

  public abstract static class AbstractWhileDispatch extends Node {
    protected final boolean predicateBool;
    protected final int depth;

    public AbstractWhileDispatch(final boolean predicateBool, final int depth) {
      this.predicateBool    = predicateBool;
      this.depth            = depth;
    }

    public abstract SObject executeDispatch(VirtualFrame frame,
        SBlock loopCondition, SBlock loopBody);
  }

  public static final class UninitializedDispatch extends AbstractWhileDispatch {

    public UninitializedDispatch(final boolean predicateBool, final int depth) {
      super(predicateBool, depth);
    }

    @Override
    public SObject executeDispatch(final VirtualFrame frame,
        final SBlock loopCondition, final SBlock loopBody) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");


      if (depth < INLINE_CACHE_SIZE) {
        CachedDispatch specialized = new CachedDispatch(loopCondition, loopBody,
            predicateBool, depth);
        return replace(specialized).
            executeDispatch(frame, loopCondition, loopBody);
      }

      AbstractWhileDispatch headNode = determineChainHead();
      GenericDispatch generic = new GenericDispatch(predicateBool);
      return headNode.replace(generic).
          executeDispatch(frame, loopCondition, loopBody);
    }

    private AbstractWhileDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof AbstractWhileDispatch) {
        i = i.getParent();
      }
      return (AbstractWhileDispatch) i;
    }
  }

  public static final class CachedDispatch extends AbstractWhileDispatch {

    private final SInvokable condition;
    private final SInvokable body;
    @Child private WhileWithDynamicBlocksNode whileNode;
    @Child private AbstractWhileDispatch next;

    public CachedDispatch(final SBlock condition, final SBlock body,
        final boolean predicateBool, final int depth) {
      super(predicateBool, depth);
      this.condition = condition.getMethod();
      this.body      = body.getMethod();
      this.next = new UninitializedDispatch(predicateBool, depth + 1);
      this.whileNode = new WhileWithDynamicBlocksNode(condition, body,
          predicateBool, null);
    }

    @Override
    public SObject executeDispatch(final VirtualFrame frame, final SBlock loopCondition,
        final SBlock loopBody) {
      if (condition == loopCondition.getMethod() &&
          body      == loopBody.getMethod()) {
        return whileNode.doWhileUnconditionally(frame, loopCondition, loopBody);
      } else {
        return next.executeDispatch(frame, loopCondition, loopBody);
      }
    }
  }

  public static final class GenericDispatch extends AbstractWhileDispatch {
    public GenericDispatch(final boolean predicateBool) {
      super(predicateBool, 0);
    }

    private boolean obj2bool(final Object o) {
      if (o instanceof Boolean) {
        return (boolean) o;
      } else if (o == Globals.trueObject) {
        CompilerAsserts.neverPartOfCompilation("obj2Bool1");
        return true;
      } else {
        CompilerAsserts.neverPartOfCompilation("obj2Bool2");
        assert o == Globals.falseObject;
        return false;
      }
    }

    @Override
    public SObject executeDispatch(final VirtualFrame frame, final SBlock loopCondition,
        final SBlock loopBody) {
      CompilerAsserts.neverPartOfCompilation("WhileCache.GenericDispatch"); // no caching, direct invokes, no loop count reporting...

      Object conditionResult = loopCondition.getMethod().invoke(loopCondition);
      boolean loopConditionResult = obj2bool(conditionResult);


      // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
      while (loopConditionResult == predicateBool) {
        loopBody.getMethod().invoke(loopBody);
        conditionResult = loopCondition.getMethod().invoke(loopCondition);
        loopConditionResult = obj2bool(conditionResult);
      }
      return Nil.nilObject;
    }
  }
}
