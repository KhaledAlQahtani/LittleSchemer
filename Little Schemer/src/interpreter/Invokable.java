package som.interpreter;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Invokable extends RootNode {

  @Child protected ExpressionNode  expressionOrSequence;

  private final ExpressionNode  uninitializedBody;

  public Invokable(final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode expressionOrSequence) {
    super(sourceSection, frameDescriptor);
    this.uninitializedBody    = NodeUtil.cloneNode(expressionOrSequence);
    this.expressionOrSequence = expressionOrSequence;
  }

  public ExpressionNode getUninitializedBody() {
    return uninitializedBody;
  }

  @Override
  public final Object execute(final VirtualFrame frame) {
    return expressionOrSequence.executeGeneric(frame);
  }

  public abstract Invokable cloneWithNewLexicalContext(final LexicalContext outerContext);

  @Override
  public final boolean isCloningAllowed() {
    return true;
  }

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }

  public abstract void propagateLoopCountThroughoutLexicalScope(final long count);

  public abstract boolean isBlock();
}
