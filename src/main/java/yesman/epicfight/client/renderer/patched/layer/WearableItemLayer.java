package yesman.epicfight.client.renderer.patched.layer;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import yesman.epicfight.api.client.model.AnimatedMesh;
import yesman.epicfight.api.client.model.armor.CustomModelBakery;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.model.JsonModelLoader;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@OnlyIn(Dist.CLIENT)
public class WearableItemLayer<E extends LivingEntity, T extends LivingEntityPatch<E>, M extends HumanoidModel<E>, AM extends HumanoidMesh> extends PatchedLayer<E, T, M, HumanoidArmorLayer<E, M, M>, AM> {
	private static final Map<ResourceLocation, AnimatedMesh> ARMOR_MODELS = new HashMap<ResourceLocation, AnimatedMesh>();
	private static final Map<String, ResourceLocation> EPICFIGHT_OVERRIDING_TEXTURES = Maps.newHashMap();
	
	public static void clear() {
		ARMOR_MODELS.clear();
		EPICFIGHT_OVERRIDING_TEXTURES.clear();
	}
	
	private final boolean firstPersonModel;
	
	public WearableItemLayer(AM mesh, boolean firstPersonModel) {
		super(mesh);
		
		this.firstPersonModel = firstPersonModel;
	}
	
	private void renderArmor(PoseStack matStack, MultiBufferSource multiBufferSource, int packedLightIn, boolean hasEffect, AnimatedMesh model, Armature armature,
								float r, float g, float b, ResourceLocation armorTexture, OpenMatrix4f[] poses) {
		VertexConsumer vertexConsumer = EpicFightRenderTypes.getArmorFoilBufferTriangles(multiBufferSource, RenderType.armorCutoutNoCull(armorTexture), false, hasEffect);
		model.drawModelWithPose(matStack, vertexConsumer, packedLightIn, r, g, b, 1.0F, OverlayTexture.NO_OVERLAY, armature, poses);
	}
	
	@Override
	public void renderLayer(T entitypatch, E entityliving, HumanoidArmorLayer<E, M, M> vanillaLayer, PoseStack poseStack, MultiBufferSource buf, int packedLightIn,
								OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.ARMOR) {
				continue;
			}
			
			boolean chestPart = false;
			
			if (entitypatch.isFirstPerson() && this.firstPersonModel) {
				if (slot != EquipmentSlot.CHEST) {
					continue;
				} else {
					chestPart = true;
				}
			}
			
			if (slot == EquipmentSlot.HEAD && this.firstPersonModel) {
				continue;
			}
			
			ItemStack stack = entityliving.getItemBySlot(slot);
			Item item = stack.getItem();
			
			if (item instanceof ArmorItem armorItem) {
				if (slot != armorItem.getSlot()) {
					return;
				}
				
				poseStack.pushPose();
				float head = 0.0F;
				
				if (slot == EquipmentSlot.HEAD) {
					poseStack.translate(0.0D, head * 0.055D, 0.0D);
				}
				
				boolean debuggingMode = ClientEngine.getInstance().isArmorModelDebuggingMode();
				
				if (debuggingMode) {
					poseStack.pushPose();
					poseStack.scale(-1.0F, -1.0F, 1.0F);
					poseStack.translate(1.0D, -1.501D, 0.0D);
					vanillaLayer.render(poseStack, buf, packedLightIn, entityliving, partialTicks, head, packedLightIn, bob, yRot, xRot);
					poseStack.popPose();
				}
				
				AnimatedMesh armorMesh = this.getArmorModel(vanillaLayer, entityliving, armorItem, stack, slot, debuggingMode);
				
				if (armorMesh == null) {
					poseStack.popPose();
					return;
				}
				
				armorMesh.initialize();
				
				if (chestPart) {
					if (armorMesh.hasPart("torso")) {
						armorMesh.getPart("torso").hidden = true;
					}
				}
				
				boolean hasEffect = stack.hasFoil();
				
				if (armorItem instanceof DyeableLeatherItem dyeableItem) {
					int i = dyeableItem.getColor(stack);
					float r = (float) (i >> 16 & 255) / 255.0F;
					float g = (float) (i >> 8 & 255) / 255.0F;
					float b = (float) (i & 255) / 255.0F;
					
					this.renderArmor(poseStack, buf, packedLightIn, hasEffect, armorMesh, entitypatch.getArmature(), r, g, b, this.getArmorTexture(stack, entityliving, armorMesh, slot, null), poses);
					this.renderArmor(poseStack, buf, packedLightIn, hasEffect, armorMesh, entitypatch.getArmature(), 1.0F, 1.0F, 1.0F, this.getArmorTexture(stack, entityliving, armorMesh, slot, "overlay"), poses);
				} else {
					this.renderArmor(poseStack, buf, packedLightIn, hasEffect, armorMesh, entitypatch.getArmature(), 1.0F, 1.0F, 1.0F, this.getArmorTexture(stack, entityliving, armorMesh, slot, null), poses);
				}
				
				poseStack.popPose();
			}
		}
	}
	
	private AnimatedMesh getArmorModel(HumanoidArmorLayer<E, M, M> originalRenderer, E entityliving, ArmorItem armorItem, ItemStack stack, EquipmentSlot slot, boolean armorDebugging) {
		ResourceLocation registryName = armorItem.getRegistryName();
		
		if (ARMOR_MODELS.containsKey(registryName) && !armorDebugging) {
			return ARMOR_MODELS.get(registryName);
		} else {
			ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
			ResourceLocation rl = new ResourceLocation(armorItem.getRegistryName().getNamespace(), "animmodels/armor/" + armorItem.getRegistryName().getPath() + ".json");
			AnimatedMesh model = null;
			
			if (resourceManager.hasResource(rl)) {
				JsonModelLoader modelLoader = new JsonModelLoader(resourceManager, rl);
				model = modelLoader.loadAnimatedMesh(AnimatedMesh::new);
			} else {
				HumanoidModel<?> defaultModel = originalRenderer.getArmorModel(slot);
				Model customModel = ForgeHooksClient.getArmorModel(entityliving, stack, slot, defaultModel);
				
				if (customModel == defaultModel || !(customModel instanceof HumanoidModel<?> humanoidModel)) {
					model = this.mesh.getHumanoidArmorModel(slot);
				} else {
					model = CustomModelBakery.bake(humanoidModel, armorItem, slot, armorDebugging);
				}
			}
			
			ARMOR_MODELS.put(registryName, model);
			
			return model;
		}
	}
	
	private ResourceLocation getArmorTexture(ItemStack stack, Entity entity, AnimatedMesh armorMesh, EquipmentSlot slot, String type) {
		ArmorItem item = (ArmorItem) stack.getItem();
		String texture = item.getMaterial().getName();
		String domain = "minecraft";
		int idx = texture.indexOf(':');
		
		if (idx != -1) {
			domain = texture.substring(0, idx);
			texture = texture.substring(idx + 1);
		}
		
		String s1 = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture, (slot == EquipmentSlot.LEGS ? 2 : 1), type == null ? "" : String.format("_%s", type));
		s1 = ForgeHooksClient.getArmorTexture(entity, stack, s1, slot, type);
		int idx2 = s1.lastIndexOf('/');
		String s2 = String.format("%s/epicfight/%s", s1.substring(0, idx2), s1.substring(idx2 + 1));
		ResourceLocation resourcelocation2 = EPICFIGHT_OVERRIDING_TEXTURES.get(s2);
		
		if (resourcelocation2 != null) {
			return resourcelocation2;
		} else if (!EPICFIGHT_OVERRIDING_TEXTURES.containsKey(s2)) {
			resourcelocation2 = new ResourceLocation(s2);
			ResourceManager rm = Minecraft.getInstance().getResourceManager();
			
			if (rm.hasResource(resourcelocation2)) {
				EPICFIGHT_OVERRIDING_TEXTURES.put(s2, resourcelocation2);
				return resourcelocation2;
			} else {
				EPICFIGHT_OVERRIDING_TEXTURES.put(s2, null);
			}
		}
		
		if (armorMesh.getRenderProperty() != null && armorMesh.getRenderProperty().getCustomTexturePath() != null) {
			s1 = armorMesh.getRenderProperty().getCustomTexturePath();
		}
		
		ResourceLocation resourcelocation = HumanoidArmorLayer.ARMOR_LOCATION_CACHE.get(s1);
		
		if (resourcelocation == null) {
			resourcelocation = new ResourceLocation(s1);
			HumanoidArmorLayer.ARMOR_LOCATION_CACHE.put(s1, resourcelocation);
		}
		
		return resourcelocation;
	}
}