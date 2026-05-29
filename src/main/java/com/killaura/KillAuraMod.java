package com.killaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.Random;

public class KillAuraMod implements ClientModInitializer {
    private static boolean enabled = false;
    private static long lastAttack = 0;
    private static final Random random = new Random();

    @Override
    public void onInitializeClient() {
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.killaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.killaura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Включение/выключение по R
            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§6[KillAura] §f" + status), true);
                if (enabled) {
                    client.player.sendMessage(Text.literal("§7Обходы: §aRandomDelay | RandomAngles | GCD | NoClickDelay"), true);
                }
            }

            if (!enabled) return;

            long now = System.currentTimeMillis();
            
            // Обход 1: рандомная задержка 1.4-2.0 секунды
            int delay = 1400 + random.nextInt(600);
            if (now - lastAttack < delay) return;

            for (var entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) continue;
                
                double distance = client.player.distanceTo(entity);
                if (distance > 4.2) continue;

                // Расчёт углов
                float yaw = (float)(Math.toDegrees(Math.atan2(
                    entity.getZ() - client.player.getZ(),
                    entity.getX() - client.player.getX()
                )) - 90);
                
                float pitch = (float)(-Math.toDegrees(Math.atan2(
                    entity.getY() + entity.getHeight() / 2 - (client.player.getY() + client.player.getHeight() / 2),
                    Math.sqrt(Math.pow(entity.getX() - client.player.getX(), 2) + Math.pow(entity.getZ() - client.player.getZ(), 2))
                )));

                // Обход 2: рандомизация углов (±2-8 градусов)
                yaw += (random.nextFloat() - 0.5f) * 8;
                pitch += (random.nextFloat() - 0.5f) * 5;

                // Обход 3: GCD Bypass (округление)
                yaw = Math.round(yaw * 10) / 10.0f;
                pitch = Math.round(pitch * 10) / 10.0f;

                // Поворот и атака
                client.player.setYaw(yaw);
                client.player.setPitch(pitch);
                client.interactionManager.attackEntity(client.player, entity);
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                
                lastAttack = now;
                break;
            }
        });
    }
}
