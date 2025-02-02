package com.williambl.haema.client

import com.williambl.haema.ability.AbilityModule
import com.williambl.haema.ability.component.invisibility.InvisibilityAbilityComponent
import com.williambl.haema.ability.component.mist_form.MistFormAbilityComponent
import com.williambl.haema.api.client.VampireHudAddTextEvent
import com.williambl.haema.client.config.HaemaConfig
import com.williambl.haema.client.gui.RitualTableScreen
import com.williambl.haema.client.gui.VampireHud
import com.williambl.haema.component.EntityVampireComponent
import com.williambl.haema.component.EntityVampireComponent.Companion.getFeedCooldown
import com.williambl.haema.drip.DripModule
import com.williambl.haema.getAbilityLevel
import com.williambl.haema.hunter.VampireHunterModule
import com.williambl.haema.id
import com.williambl.haema.isVampire
import com.williambl.haema.ritual.RitualModule
import com.williambl.haema.ritual.RitualTableScreenHandler
import com.williambl.haema.vampireComponent
import com.williambl.haema.vampiremobs.VampireMobsModule
import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import org.lwjgl.glfw.GLFW
import software.bernie.geckolib3.renderers.geo.GeoArmorRenderer
import kotlin.math.max

object HaemaClient: ClientModInitializer {
    val VAMPIRE_SHADER: ManagedShaderEffect = ShaderEffectManager.getInstance()
        .manage(id("shaders/post/vampirevision.json"))

    val HAEMA_KEY_CATEGORY = "key.categories.haema"

    val DASH_KEY = KeyBinding("key.haema.dash", GLFW.GLFW_KEY_Z, HAEMA_KEY_CATEGORY)

    val config: HaemaConfig by lazy { AutoConfig.getConfigHolder(HaemaConfig::class.java).config }

    var invisLengthValue = 10

    var distortAmount = 0.0f
        set(value) {
            field = (value * config.distortionAdjust * MinecraftClient.getInstance().options.distortionEffectScale.value).toFloat()
            VAMPIRE_SHADER.setUniformValue("DistortAmount", field)
        }

    fun setRedAmount(value: Float) {
        VAMPIRE_SHADER.setUniformValue("RedMatrix", value, 0.0f, 0.0f)
    }

    fun setBrightnessAdjust(value: Float) {
        VAMPIRE_SHADER.setUniformValue("BrightnessAdjust", value*config.brightAdjust)
    }

    fun setSaturation(value: Float) {
        VAMPIRE_SHADER.setUniformValue("Saturation", 1f-((1f-value)*config.saturationAdjust))
    }

    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(id("updateinvislength")) { client, handler, buf, sender ->
            invisLengthValue = buf.readInt()
        }
        ClientPlayNetworking.registerGlobalReceiver(id("start_mist_form")) { client, handler, buf, sender ->
            val entityId = buf.readVarInt()
            client.execute {
                val entity = client.world?.getEntityById(entityId) ?: return@execute
                client.entityRenderDispatcher.render(entity, entity.x, entity.y, entity.z, entity.yaw, 1f, MatrixStack(), ParticleVertexConsumer.Provider(), 1)
            }
        }

        HudRenderCallback.EVENT.register(VampireHud::render)

        ShaderEffectRenderCallback.EVENT.register(ShaderEffectRenderCallback {
            val cam = MinecraftClient.getInstance().cameraEntity
            if (config.vampireShaderEnabled && cam is LivingEntity && cam.isVampire && cam.getAbilityLevel(AbilityModule.VISION) > 0) {
                VAMPIRE_SHADER.render(it)
            }
        })

        KeyBindingHelper.registerKeyBinding(DASH_KEY)

        ClientMistHandler.init()

        EntityModelLayerRegistry.registerModelLayer(VampireHunterModel.layer, VampireHunterModel.Companion::getTexturedModelData)
        EntityModelLayerRegistry.registerModelLayer(VampiragerModel.layer, VampiragerModel.Companion::getTexturedModelData)
        EntityRendererRegistry.register(VampireHunterModule.VAMPIRE_HUNTER) { context -> VampireHunterEntityRenderer(context) }
        EntityRendererRegistry.register(VampireMobsModule.VAMPIRIC_ZOMBIE) { context -> VampiricZombieRenderer(context) }
        EntityRendererRegistry.register(VampireMobsModule.VAMPIRAGER) { context -> VampiragerEntityRenderer(context) }

        GeoArmorRenderer.registerArmorRenderer<Entity>(VampireClothingRenderer(), DripModule.VAMPIRE_HAT, DripModule.VAMPIRE_COAT, DripModule.VAMPIRE_TROUSERS, DripModule.VAMPIRE_SHOES)

        AutoConfig.register(HaemaConfig::class.java) { config, clazz -> Toml4jConfigSerializer(config, clazz) }

        HandledScreens.register(RitualModule.RITUAL_TABLE_SCREEN_HANDLER) {
                screenHandler: RitualTableScreenHandler, inv: PlayerInventory, title: Text -> RitualTableScreen(screenHandler, inv, title)
        }

        VampireHudAddTextEvent.EVENT.register(VampireHudAddTextEvent { player, createText ->
            if (ClientDashHandler.couldDash(player)) {
                return@VampireHudAddTextEvent listOf(createText(
                    DASH_KEY.boundKeyLocalizedText.copy(),
                    ClientDashHandler.canDash(player),
                    Text.translatable("gui.haema.hud.vampiredash")
                ))
            }
            return@VampireHudAddTextEvent emptyList()
        })

        VampireHudAddTextEvent.EVENT.register(VampireHudAddTextEvent { player, createText ->
            val invisLevel = (player).getAbilityLevel(AbilityModule.INVISIBILITY)
            if (invisLevel > 0 && (player.vampireComponent).blood >= 18f) {
                return@VampireHudAddTextEvent listOf(
                    createText(
                        MinecraftClient.getInstance().options.sneakKey.boundKeyLocalizedText.copy(),
                        (player).isVampire && player.world.time - InvisibilityAbilityComponent.entityKey.get(player).invisTicks >= 120 + invisLevel * invisLengthValue,
                        Text.translatable("gui.haema.hud.invisibility")
                    )
                )
            }
            return@VampireHudAddTextEvent emptyList()
        })

        VampireHudAddTextEvent.EVENT.register(VampireHudAddTextEvent { player, createText ->
            if (ClientMistHandler.hasMistAbility(player)) {
                return@VampireHudAddTextEvent listOf(
                    createText(
                        ClientMistHandler.MIST_KEY.boundKeyLocalizedText.copy(),
                        (player).isVampire && MistFormAbilityComponent.entityKey.get(player).canUseMistForm(),
                        Text.translatable(if (MistFormAbilityComponent.entityKey[player].isInMistForm) "gui.haema.hud.normal_form" else "gui.haema.hud.mist_form")
                    )
                )
            }
            return@VampireHudAddTextEvent emptyList()
        })

        VampireHudAddTextEvent.EVENT.register(VampireHudAddTextEvent { player, createText ->
            if (ClientMistHandler.isInMistForm(player)) {
                return@VampireHudAddTextEvent listOf(
                    createText(
                        ClientMistHandler.EXPAND_MIST_KEY.boundKeyLocalizedText.copy(),
                        (player).isVampire && ClientMistHandler.canExpandMist(player),
                        Text.translatable("gui.haema.hud.expand_mist_form")
                    )
                )
            }
            return@VampireHudAddTextEvent emptyList()
        })

        VampireHudAddTextEvent.EVENT.register(VampireHudAddTextEvent { player, createText ->
            val texts = mutableListOf<Text>()
            val mc = MinecraftClient.getInstance()
            if (mc.crosshairTarget != null && mc.crosshairTarget!!.type == HitResult.Type.ENTITY) {
                val lookingAt = (mc.crosshairTarget as EntityHitResult).entity.type
                if (lookingAt.isIn(EntityVampireComponent.poorBloodTag) || lookingAt.isIn(EntityVampireComponent.mediumBloodTag) || lookingAt.isIn(EntityVampireComponent.goodBloodTag)) {
                    if (player.isSneaking) {
                        texts.add(
                            Text.translatable("gui.haema.hud.bloodquality").append(
                                when {
                                    lookingAt.isIn(EntityVampireComponent.goodBloodTag) -> Text.translatable("gui.haema.hud.bloodquality.good").formatted(
                                        Formatting.GREEN
                                    )
                                    lookingAt.isIn(EntityVampireComponent.mediumBloodTag) -> Text.translatable("gui.haema.hud.bloodquality.medium").formatted(
                                        Formatting.YELLOW
                                    )
                                    else -> Text.translatable("gui.haema.hud.bloodquality.poor").formatted(Formatting.RED)
                                }
                            )
                        )
                    }

                    texts.add(
                        createText(
                            Text.translatable("key.sneak").append(" + ").append(mc.options.useKey.boundKeyLocalizedText),
                            true,
                            Text.translatable("gui.haema.hud.feed")
                        )
                    )
                }
            }
            return@VampireHudAddTextEvent texts
        })

        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.cameraEntity ?: return@register
            if (player !is LivingEntity || !player.isVampire) {
                return@register
            }

            val world = player.world

            val bloodLevel: Float = player.vampireComponent.blood.toFloat() / 20.0f
            setSaturation(0.8f * bloodLevel)
            setBrightnessAdjust(bloodLevel / 4f + 0.05f)
            setRedAmount(
                max(
                    1.3f,
                    2.3f - (world.time - player.vampireComponent.lastFed) / getFeedCooldown(world).toFloat()
                )
            )
        }

        ClientTickEvents.START_CLIENT_TICK.register(ClientDashHandler)
        ClientTickEvents.START_CLIENT_TICK.register(ClientMistHandler)

        ParticleFactoryRegistry.getInstance().register(AbilityModule.MIST_PARTICLE, MistParticle::Factory)
    }
}