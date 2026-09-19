package de.petrofsky.autonomousvillagers.villagers.appearence;

import com.mojang.blaze3d.vertex.PoseStack;
import de.petrofsky.autonomousvillagers.villagers.VillagerProfessions;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.NotNull;

/**
 * Custom hierarchical model for villagers that supports individual arm movement and item holding.
 * <p>
 * Vanilla villagers use a single, static "arms" block folded across their chest. This model
 * introduces separate {@code rightArm} and {@code leftArm} parts. Depending on the villager's
 * profession, it dynamically toggles between the classic folded arms and the articulated arms
 * needed for holding and swinging tools (like axes or hoes).
 */
public class ArmedVillagerModel extends HierarchicalModel<Villager>
        implements HeadedModel, VillagerHeadModel, ArmedModel {

    private final ModelPart root;
    private final ModelPart head, hat, hatRim;
    private final ModelPart rightLeg, leftLeg;
    private final ModelPart rightArm, leftArm;
    private final ModelPart arms;

    /**
     * Constructs the model by extracting specific parts from the provided root geometry.
     *
     * @param root The root model part containing all child meshes.
     */
    public ArmedVillagerModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.hat = this.head.getChild("hat");
        this.hatRim = this.hat.getChild("hat_rim");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.arms = root.getChild("arms");
    }

    /**
     * Determines if the current villager should use separated arms based on its profession.
     *
     * @param entity The villager entity to check.
     * @return {@code true} if the villager is a modded farmer or forester; {@code false} otherwise.
     */
    private static boolean hasArmedProfession(Villager entity) {
        VillagerProfession profession = entity.getVillagerData().getProfession();
        return profession == VillagerProfessions.MODDED_FARMER.value()
                || profession == VillagerProfessions.MODDED_FORESTER.value();
    }


    @Override public @NotNull ModelPart root() { return this.root; }
    @Override public @NotNull ModelPart getHead() { return this.head; }
    @Override public void hatVisible(boolean visible) {
        this.hat.visible = visible;
        this.hatRim.visible = visible;
    }

    /**
     * Aligns the provided PoseStack to the specific arm's current position and rotation.
     * <p>
     * This is required by {@link net.minecraft.client.renderer.entity.layers.ItemInHandLayer}
     * to correctly render items (like tools) physically attached to the villager's moving hands.
     *
     * @param arm       The arm (left or right) holding the item.
     * @param poseStack The transformation stack to translate.
     */
    @Override
    public void translateToHand(@NotNull HumanoidArm arm, @NotNull PoseStack poseStack) {
        this.getArm(arm).translateAndRotate(poseStack);
    }

    /**
     * Retrieves the corresponding model part for the given anatomical arm.
     *
     * @param arm The {@link HumanoidArm} enum value.
     * @return The left or right arm {@link ModelPart}.
     */
    private ModelPart getArm(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
    }

    /**
     * Calculates and applies rotations to the model parts for the current frame.
     * <p>
     * Handles basic locomotion (walking legs, head turning), unhappy head shaking,
     * and the dynamic swapping between folded arms and articulated arms. If the villager
     * is armed and performing an action, it applies trigonometric formulas to
     * simulate a realistic tool-swinging animation based on the entity's {@code attackTime}.
     *
     * @param entity          The villager entity being animated.
     * @param limbSwing       The cumulative distance the entity has moved.
     * @param limbSwingAmount The current speed of the entity's movement.
     * @param ageInTicks      The age of the entity in ticks (used for continuous animations like shaking).
     * @param netHeadYaw      The horizontal head rotation (yaw).
     * @param headPitch       The vertical head rotation (pitch).
     */
    @Override
    public void setupAnim(Villager entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        boolean flag = entity.getUnhappyCounter() > 0;

        // Head rotations
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);
        if (flag) {
            this.head.zRot = 0.3F * Mth.sin(0.45F * ageInTicks);
            this.head.xRot = 0.4F;
        } else {
            this.head.zRot = 0.0F;
        }

        // Leg walking animations
        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5F;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount * 0.5F;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;

        // Toggle visibility between vanilla crossed arms and custom separated arms
        boolean armed = hasArmedProfession(entity);
        this.arms.visible = !armed;
        this.rightArm.visible = armed;
        this.leftArm.visible = armed;

        // Skip arm animation if using vanilla folded arms
        if (!armed) {
            return;
        }

        // Base arm swinging during walking
        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount * 0.5F;
        this.leftArm.xRot  = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot  = 0.0F;

        // Advanced swinging animation (e.g., chopping trees, hoeing dirt)
        if (this.attackTime > 0.0F) {
            HumanoidArm arm = entity.getMainArm();
            ModelPart swinging = (arm == HumanoidArm.RIGHT) ? this.rightArm : this.leftArm;

            // Calculate swing progress using a cubic curve for a natural striking motion
            float f = this.attackTime;
            float f1 = 1.0F - f;
            f1 = f1 * f1; f1 = f1 * f1; f1 = 1.0F - f1;
            float f2 = Mth.sin(f1 * (float) Math.PI);
            float f3 = Mth.sin(f * (float) Math.PI) * -(this.head.xRot - 0.7F) * 0.75F;

            // Apply calculated offsets to the swinging arm
            swinging.xRot -= f2 * 1.2F + f3;
            swinging.zRot += Mth.sin(f * (float) Math.PI) * -0.4F;
        }
    }

    /**
     * Creates the structural geometry (mesh) for this custom model.
     * <p>
     * It inherits the base body mesh from vanilla villagers but injects custom cubes for the
     * {@code right_arm} and {@code left_arm}. It uses {@link CubeDeformation} to give
     * the arms slightly bulky sleeves and distinct hands, mapping them to specific texture coordinates.
     *
     * @return The complete layer definition ready to be registered in the client model registry.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = VillagerModel.createBodyModel();
        PartDefinition root = mesh.getRoot();

        // Deformations allow specific cubes to render slightly larger than their bounding box.
        CubeDeformation sleeveDeformation = new CubeDeformation(0.5F);
        CubeDeformation handDeformation = new CubeDeformation(1.5F);
// .addBox(/* Width offset */ -3.0F, /* Height offset */ 6.5F, /* Depth offset */ -2.0F,
              //  /* Width */ 3.2F, /* Height */ 2.0F, /* Depth */ 4.0F),
                root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        // Arm texture
                        .texOffs(44, 22)
                        // Arm geometry
                        .addBox(/* Width offset */ -3.0F,  /* Height offset */ -2.0F, /* Depth offset */ -2.0F,
                                /* Width */ 4.0F, /* Height */ 8.0F, /* Depth */ 4.0F, sleeveDeformation)
                        // Hand texture
                        .texOffs(13, 0)
                        // Hand geometry
                        .addBox(/* Width offset */ -1F,  /* Height offset */ 7.5F, /* Depth offset */ -0.5F,
                                /* Width */ 1.0F, /* Height */ 1.0F, /* Depth */ 1.0F, handDeformation),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        // Arm texture
                        .texOffs(44, 22).mirror()
                        // Arm geometry
                        .addBox(/* Width offset */ -1F, /* Height offset */ -2.0F, /* Depth offset */ -2.0F,
                                /* Width */ 4.0F, /* Height */ 8.0F, /* Depth */ 4.0F, sleeveDeformation)
                        // Hand texture
                        .texOffs(13, 0)
                        // Hand geometry
                        .addBox(/* Width offset */ 0.5F, /* Height offset */ 7.5F, /* Depth offset */ -0.5F,
                                /* Width */ 1.0F, /* Height */ 1.0F, /* Depth */ 1.0F, handDeformation),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
