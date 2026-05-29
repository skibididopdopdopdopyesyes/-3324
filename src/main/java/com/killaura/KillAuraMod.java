package com.killaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.glfw.GLFW;
import java.util.Random;

public class KillAuraMod implements ClientModInitializer {
    private static boolean enabled = true;
    private static long lastAttack = 0;
    private static long lastFakeLag = 0;
    private static final Random random = new Random();
    private static int currentMode = 0;

    @Override
    public void onInitializeClient() {
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.killaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.killaura"
        ));

        KeyBinding modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.killaura.mode",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "category.killaura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§6[KillAura] §e" + (enabled ? "ON" : "OFF")), true);
            }

            while (modeKey.wasPressed()) {
                currentMode = (currentMode + 1) % 4;
                String modeName = switch (currentMode) {
                    case 0 -> "Обычный";
                    case 1 -> "Агрессивный";
                    case 2 -> "Скрытный";
                    case 3 -> "ФейкЛаг";
                    default -> "Обычный";
                };
                client.player.sendMessage(Text.literal("§6[KillAura] §eРежим: " + modeName), true);
            }

            if (!enabled) return;

            // ===== ОБХОД 1: FAKELAG (задержка пакетов) =====
            if (currentMode == 3 || random.nextInt(100) < 30) {
                long now = System.currentTimeMillis();
                if (now - lastFakeLag < 45 + random.nextInt(30)) {
                    lastFakeLag = now;
                    return;
                }
                lastFakeLag = now;
            }

            long now = System.currentTimeMillis();
            
            // ===== ОБХОД 2: РАНДОМНАЯ ЗАДЕРЖКА (1.4-2.0 сек) =====
            int delay = 1400 + random.nextInt(600);
            if (now - lastAttack < delay) return;

            for (var entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) continue;
                
                double distance = client.player.distanceTo(entity);
                if (distance > 4.2) continue;

                // ===== ОБХОД 3: REACH SPOOF (подмена дистанции) =====
                if (currentMode != 2 && random.nextInt(100) < 40) {
                    double spoofedReach = 3.0 + random.nextDouble() * 0.5;
                    if (client.getNetworkHandler() != null) {
                        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            client.player.getX(),
                            client.player.getY() - 0.01,
                            client.player.getZ(),
                            false
                        ));
                    }
                }

                // ===== ОБХОД 4: РАСЧЁТ УГЛОВ =====
                float yaw = (float)(Math.toDegrees(Math.atan2(
                    entity.getZ() - client.player.getZ(),
                    entity.getX() - client.player.getX()
                )) - 90);
                
                float pitch = (float)(-Math.toDegrees(Math.atan2(
                    entity.getY() + entity.getHeight() / 2 - (client.player.getY() + client.player.getHeight() / 2),
                    Math.sqrt(Math.pow(entity.getX() - client.player.getX(), 2) + Math.pow(entity.getZ() - client.player.getZ(), 2))
                )));

                // ===== ОБХОД 5: РАНДОМИЗАЦИЯ УГЛОВ (±2-8°) =====
                yaw += (random.nextFloat() - 0.5f) * (currentMode == 0 ? 6 : 10);
                pitch += (random.nextFloat() - 0.5f) * (currentMode == 0 ? 4 : 7);

                // ===== ОБХОД 6: GCD BYPASS (округление) =====
                yaw = Math.round(yaw * 10) / 10.0f;
                pitch = Math.round(pitch * 10) / 10.0f;

                // ===== ОБХОД 7: SILENT AURA (атака без поворота) =====
                if (currentMode == 2 || random.nextInt(100) < 35) {
                    float oldYaw = client.player.getYaw();
                    float oldPitch = client.player.getPitch();
                    client.player.setYaw(yaw);
                    client.player.setPitch(pitch);
                    client.interactionManager.attackEntity(client.player, entity);
                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    client.player.setYaw(oldYaw);
                    client.player.setPitch(oldPitch);
                } else {
                    client.player.setYaw(yaw);
                    client.player.setPitch(pitch);
                    client.interactionManager.attackEntity(client.player, entity);
                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }

                // ===== ОБХОД 8: MISS CHANCE (намеренный промах) =====
                if (random.nextInt(100) < 12) {
                    client.player.sendMessage(Text.literal("§8[KillAura] §7Промах (имитация)"), true);
                    lastAttack = now;
                    break;
                }

                lastAttack = now;
                break;
            }
        });
    }
}
