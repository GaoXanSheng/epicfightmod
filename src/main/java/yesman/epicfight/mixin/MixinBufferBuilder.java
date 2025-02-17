package yesman.epicfight.mixin;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.primitives.Floats;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.math.Vector3f;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import net.minecraft.util.Mth;

@Mixin(value = BufferBuilder.class)
public abstract class MixinBufferBuilder {
	@Shadow private ByteBuffer buffer;
	@Shadow @Final private List<BufferBuilder.DrawState> drawStates;
	@Shadow private int totalRenderedBytes;
	@Shadow private int nextElementByte;
	@Shadow private int vertices;
	@Shadow private VertexFormatElement currentElement;
	@Shadow private int elementIndex;
	@Shadow private VertexFormat format;
	@Shadow private VertexFormat.Mode mode;
	@Shadow private boolean building;
	@Shadow private Vector3f[] sortingPoints;
	@Shadow private float sortX;
	@Shadow private float sortY;
	@Shadow private float sortZ;
	@Shadow private boolean indexOnly;
	
	@Shadow
	private IntConsumer intConsumer(VertexFormat.IndexType indexType) {throw new AbstractMethodError("Shadow");}
	
	@Shadow
	private void putSortedQuadIndices(VertexFormat.IndexType indexType) {};
	
	@Inject(at = @At(value = "HEAD"), method = "setQuadSortOrigin(FFF)V")
	public void epicfight_setQuadSortOrigin(float x, float y, float z, CallbackInfo callbackInfo) {
		if (this.mode == VertexFormat.Mode.TRIANGLES) {
			if (this.sortX != x || this.sortY != y || this.sortZ != z) {
				this.sortX = x;
				this.sortY = y;
				this.sortZ = z;
				
				if (this.sortingPoints == null) {
					this.sortingPoints = this.makeTrianglesSortingPoints();
				}
			}
		}
	}
	
	@Redirect(     at = @At(   value = "INVOKE"
			                , target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;putSortedQuadIndices(Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V")
	         , method = "end()V")
	public void epicfight_end(BufferBuilder bufferBuilder, VertexFormat.IndexType vertexformat$indextype) {
		if (this.mode == VertexFormat.Mode.QUADS) {
			this.putSortedQuadIndices(vertexformat$indextype);
		} else if (this.mode == VertexFormat.Mode.TRIANGLES) {
			this.putSortedTriangleIndices(vertexformat$indextype);
		}
	}
	
	private void putSortedTriangleIndices(VertexFormat.IndexType indexType) {
		float[] afloat = new float[this.sortingPoints.length];
		int[] aint = new int[this.sortingPoints.length];
		
		for (int i = 0; i < this.sortingPoints.length; aint[i] = i++) {
			float f = this.sortingPoints[i].x() - this.sortX;
			float f1 = this.sortingPoints[i].y() - this.sortY;
			float f2 = this.sortingPoints[i].z() - this.sortZ;
			afloat[i] = f * f + f1 * f1 + f2 * f2;
		}
		
		IntArrays.mergeSort(aint, (p_166784_, p_166785_) -> {
			return Floats.compare(afloat[p_166785_], afloat[p_166784_]);
		});
		
		IntConsumer intconsumer = this.intConsumer(indexType);
		this.buffer.position(this.nextElementByte);
		
		for (int j : aint) {
			intconsumer.accept(j * this.mode.primitiveStride + 0);
			intconsumer.accept(j * this.mode.primitiveStride + 1);
			intconsumer.accept(j * this.mode.primitiveStride + 2);
		}
	}
	
	public Vector3f[] makeTrianglesSortingPoints() {
		FloatBuffer floatbuffer = this.buffer.asFloatBuffer();
		int i = this.totalRenderedBytes / 4;
		int j = this.format.getIntegerSize();
		int k = j * this.mode.primitiveStride;
		int l = this.vertices / this.mode.primitiveStride;
		Vector3f[] avector3f = new Vector3f[l];
		
		for (int i1 = 0; i1 < l; ++i1) {
			float x1 = floatbuffer.get(i + i1 * k + 0);
			float y1 = floatbuffer.get(i + i1 * k + 1);
			float z1 = floatbuffer.get(i + i1 * k + 2);
			float x2 = floatbuffer.get(i + i1 * k + j + 0);
			float y2 = floatbuffer.get(i + i1 * k + j + 1);
			float z2 = floatbuffer.get(i + i1 * k + j + 2);
			float x3 = floatbuffer.get(i + i1 * k + j * 2 + 0);
			float y3 = floatbuffer.get(i + i1 * k + j * 2 + 1);
			float z3 = floatbuffer.get(i + i1 * k + j * 2 + 2);
			
			avector3f[i1] = this.getOriginQuadCenter(x1, y1, z1, x2, y2, z2, x3, y3, z3);
		}
		
		return avector3f;
	}
	
	private Vector3f getOriginQuadCenter(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		float[] lineLength = new float[3];
		lineLength[0] = Mth.sqrt(Mth.square(x2 - x1) + Mth.square(y2 - y1) + Mth.square(z2 - z1));
		lineLength[1] = Mth.sqrt(Mth.square(x3 - x2) + Mth.square(y3 - y2) + Mth.square(z3 - z2));
		lineLength[2] = Mth.sqrt(Mth.square(x1 - x3) + Mth.square(y1 - y3) + Mth.square(z1 - z3));
		int longest = 0;
		
		for (int i = 1; i < 3; i++) {
			if (lineLength[i] > lineLength[longest]) {
				longest = i;
			}
		}
		
		switch (longest) {
		case 0:
			return new Vector3f((x1 + x2) * 0.5F, (y1 + y2) * 0.5F, (z1 + z2) * 0.5F);
		case 1:
			return new Vector3f((x2 + x3) * 0.5F, (y2 + y3) * 0.5F, (z2 + z3) * 0.5F);
		case 2:
			return new Vector3f((x3 + x1) * 0.5F, (y3 + y1) * 0.5F, (z3 + z1) * 0.5F);
		}
		
		return null;
	}
}