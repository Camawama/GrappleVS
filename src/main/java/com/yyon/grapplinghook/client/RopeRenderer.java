package com.yyon.grapplinghook.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a sagging rope span as a textured quad-strip box. Shared by the grappling hook renderer
 * and the lead overhaul, so both look like the same rope.
 */
@OnlyIn(Dist.CLIENT)
public class RopeRenderer {

    public static final ResourceLocation ROPE_TEXTURES = new ResourceLocation("grapplemod", "textures/entity/rope.png");
    public static final RenderType ROPE_RENDER = RenderType.entitySolid(ROPE_TEXTURES);

    /** Draw one rope span from start to finish (both relative to the current pose origin). */
    public static void drawSegment(Vec start, Vec finish, double taut, VertexConsumer vertexbuffer, Matrix4f matrix, Matrix3f matrix3, int light) {
        if (start.sub(finish).length() < 0.05) {
            return;
        }

        int number_squares = 16;
        if (taut == 1.0F) {
            number_squares = 1;
        }

        Vec diff = finish.sub(start);

        Vec forward = diff.changeLen(1);
        Vec up = forward.cross(new Vec(1, 0, 0));
        if (up.length() == 0) {
            up = forward.cross(new Vec(0, 0, 1));
        }
        up.changeLen_ip(0.025);
        Vec side = forward.cross(up);
        side.changeLen_ip(0.025);

        Vec[] corners = new Vec[] {up.mult(-1).add(side.mult(-1)), up.add(side.mult(-1)), up.add(side), up.mult(-1).add(side)};

        for (int size = 0; size < 4; size++) {
            Vec corner1 = corners[size];
            Vec corner2 = corners[(size + 1) % 4];

            Vec normal1 = corner1.normalize();
            Vec normal2 = corner2.normalize();

            for (int square_num = 0; square_num < number_squares; square_num++)
            {
                float squarefrac1 = (float)square_num / (float) number_squares;
                Vec pos1 = start.add(diff.mult(squarefrac1));
                pos1.y += - (1 - taut) * (0.25 - Math.pow((squarefrac1 - 0.5), 2)) * 1.5;
                float squarefrac2 = ((float) square_num+1) / (float) number_squares;
                Vec pos2 = start.add(diff.mult(squarefrac2));
                pos2.y += - (1 - taut) * (0.25 - Math.pow((squarefrac2 - 0.5), 2)) * 1.5;

                Vec corner1pos1 = pos1.add(corner1);
                Vec corner2pos1 = pos1.add(corner2);
                Vec corner1pos2 = pos2.add(corner1);
                Vec corner2pos2 = pos2.add(corner2);

                vertexbuffer.vertex(matrix, (float) corner1pos1.x, (float) corner1pos1.y, (float) corner1pos1.z).color(255, 255, 255, 255).uv(0, squarefrac1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix3, (float) normal1.x, (float) normal1.y, (float) normal1.z).endVertex();
                vertexbuffer.vertex(matrix, (float) corner2pos1.x, (float) corner2pos1.y, (float) corner2pos1.z).color(255, 255, 255, 255).uv(1, squarefrac1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix3, (float) normal2.x, (float) normal2.y, (float) normal2.z).endVertex();
                vertexbuffer.vertex(matrix, (float) corner2pos2.x, (float) corner2pos2.y, (float) corner2pos2.z).color(255, 255, 255, 255).uv(1, squarefrac2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix3, (float) normal2.x, (float) normal2.y, (float) normal2.z).endVertex();
                vertexbuffer.vertex(matrix, (float) corner1pos2.x, (float) corner1pos2.y, (float) corner1pos2.z).color(255, 255, 255, 255).uv(0, squarefrac2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix3, (float) normal1.x, (float) normal1.y, (float) normal1.z).endVertex();
            }
        }
    }
}
