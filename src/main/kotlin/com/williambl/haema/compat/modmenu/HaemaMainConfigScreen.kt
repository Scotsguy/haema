package com.williambl.haema.compat.modmenu

import com.mojang.blaze3d.systems.RenderSystem
import com.williambl.haema.client.config.HaemaConfig
import com.williambl.haema.id
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.gui.DrawableHelper
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting


class HaemaMainConfigScreen(private val parent: Screen?) : Screen(Text.literal("HAEMA").formatted(Formatting.UNDERLINE)) {
    val icon = id("icon.png")

    override fun init() {
        super.init()
        addDrawableChild(ButtonWidget(width / 4 - 75, 120, 150, 20, Text.translatable("gui.haema.config.client")) {
            client?.setScreen(AutoConfig.getConfigScreen(HaemaConfig::class.java, this).get())
        })
        addDrawableChild(ButtonWidget(3 * width / 4 - 75, 120, 150, 20, Text.translatable("gui.haema.config.gameplay")) {
            client?.setScreen(HaemaGameplayConfigScreen(this))
        })
        addDrawableChild(ButtonWidget(width / 2 - 100, 180, 200, 20, Text.translatable("gui.done")) {
            close()
        })
    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
        RenderSystem.setShaderTexture(0, icon)
        DrawableHelper.drawTexture(
                matrices,
                width / 2 - 20,
                20,
                0f,
                0f,
                40,
                40,
                40,
                40
        )
        DrawableHelper.drawCenteredText(matrices, textRenderer, Text.translatable("gui.haema.title").formatted(Formatting.UNDERLINE), width / 2, 75, 0xffffff)
    }

    override fun close() {
        client?.setScreen(parent)
    }
}