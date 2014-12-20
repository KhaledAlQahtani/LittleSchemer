/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.interpreter.nodes;

import som.interpreter.FrameOnStackMarker;
import som.interpreter.Inliner;
import som.interpreter.ReturnException;
import som.interpreter.SArguments;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.BranchProfile;

public final class ReturnNonLocalNode extends ContextualNode {

  @Child private ExpressionNode expression;
  private final BranchProfile blockEscaped;
  private final FrameSlot frameOnStackMarker;

  public ReturnNonLocalNode(final ExpressionNode expression,
      final FrameSlot frameOnStackMarker,
      final int outerSelfContextLevel,
      final SourceSection source) {
    super(outerSelfContextLevel, source);
    this.expression = expression;
    this.blockEscaped = BranchProfile.create();
    this.frameOnStackMarker = frameOnStackMarker;
  }

  public ReturnNonLocalNode(final ReturnNonLocalNode node,
      final FrameSlot inlinedFrameOnStack) {
    this(node.expression, inlinedFrameOnStack,
        node.contextLevel, node.getSourceSection());
  }

  private FrameOnStackMarker getMarkerFromContext(final MaterializedFrame ctx) {
    return (FrameOnStackMarker) FrameUtil.getObjectSafe(ctx, frameOnStackMarker);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object result = expression.executeGeneric(frame);

    MaterializedFrame ctx = determineContext(frame);
    FrameOnStackMarker marker = getMarkerFromContext(ctx);

    if (marker.isOnStack()) {
      throw new ReturnException(result, marker);
    } else {
      blockEscaped.enter();
      SBlock block = (SBlock) SArguments.rcvr(frame);
      Object self = SArguments.rcvr(ctx);
      return SAbstractObject.sendEscapedBlock(self, block);
    }
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    FrameSlot inlinedFrameOnStack  = inliner.getFrameSlot(this, frameOnStackMarker.getIdentifier());
    assert inlinedFrameOnStack  != null;
    replace(new ReturnNonLocalNode(this, inlinedFrameOnStack));
  }

  public static final class CatchNonLocalReturnNode extends ExpressionNode {
    @Child protected ExpressionNode methodBody;
    private final BranchProfile nonLocalReturnHandler;
    private final BranchProfile doCatch;
    private final BranchProfile doPropagate;
    private final FrameSlot frameOnStackMarker;

    public CatchNonLocalReturnNode(final ExpressionNode methodBody,
        final FrameSlot frameOnStackMarker) {
      super(null);
      this.methodBody = methodBody;
      this.nonLocalReturnHandler = BranchProfile.create();
      this.frameOnStackMarker    = frameOnStackMarker;

      this.doCatch = BranchProfile.create();
      this.doPropagate = BranchProfile.create();
    }

    @Override
    public ExpressionNode getFirstMethodBodyNode() {
      return methodBody;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      FrameOnStackMarker marker = new FrameOnStackMarker();
      frameOnStackMarker.setKind(FrameSlotKind.Object);
      frame.setObject(frameOnStackMarker, marker);

      try {
        return methodBody.executeGeneric(frame);
      } catch (ReturnException e) {
        nonLocalReturnHandler.enter();
        if (!e.reachedTarget(marker)) {
          doPropagate.enter();
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          doCatch.enter();
          return e.result();
        }
      } finally {
        marker.frameNoLongerOnStack();
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot inlinedFrameOnStackMarker = inliner.getLocalFrameSlot(frameOnStackMarker.getIdentifier());
      assert inlinedFrameOnStackMarker != null;
      replace(new CatchNonLocalReturnNode(methodBody, inlinedFrameOnStackMarker));
    }
  }
}
