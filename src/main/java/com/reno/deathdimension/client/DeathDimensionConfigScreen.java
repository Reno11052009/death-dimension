package com.reno.deathdimension.client;

import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReturnMode;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.network.ConfigUpdatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

public final class DeathDimensionConfigScreen extends Screen {
    private enum Tab {
        GENERAL("gui.deathdimension.tab.general"),
        DIMENSION("gui.deathdimension.tab.dimension"),
        COOLDOWN("gui.deathdimension.tab.cooldown"),
        RETURN("gui.deathdimension.tab.return"),
        EFFECTS("gui.deathdimension.tab.effects");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    @Nullable
    private final Screen parent;
    private Tab activeTab = Tab.GENERAL;

    // Editable draft values
    private boolean enableMod;
    private boolean enableDeathTeleport;
    private boolean enableFreeMovement;
    private boolean preventVoidDamage;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private boolean useSharedSpawn;
    private boolean preventDimensionEscape;
    private boolean preserveInventory;
    private boolean preserveExperience;
    private boolean restorePlayerAbilities;
    private boolean returnToDeathLocation;
    private boolean enableCooldown;
    private long cooldownDuration;
    private boolean showCooldownMessage;
    private boolean showCooldownActionbar;
    private boolean allowAdminBypass;
    private boolean enableReturnSystem;
    private ReturnMode returnMode;
    private boolean returnToOriginalDimension;
    private boolean returnToOriginalPosition;
    private boolean allowAdminReturn;
    private boolean skipDeathScreen;
    private boolean enableVoidParticles;
    private boolean enableDeathTitle;
    private boolean enableReturnTitle;
    private boolean enableEyeOpenEffect;
    private boolean enableReviveAnimation;
    private ReviveAnimationStyle reviveAnimationStyle;

    // Numeric input boxes on active tab
    @Nullable
    private EditBox spawnXBox;
    @Nullable
    private EditBox spawnYBox;
    @Nullable
    private EditBox spawnZBox;
    @Nullable
    private EditBox cooldownDurationBox;
    @Nullable
    private Component errorMessage;

    public DeathDimensionConfigScreen(@Nullable Screen parent, ModConfig.ConfigSnapshot initial) {
        super(Component.translatable("gui.deathdimension.config.title"));
        this.parent = parent;
        loadFromSnapshot(initial);
    }

    public static Screen createFromModList(@Nullable Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.hasPermissions(2)) {
            return new OpDeniedScreen(parent);
        }
        return new DeathDimensionConfigScreen(parent, ModConfig.captureSnapshot());
    }

    private void loadFromSnapshot(ModConfig.ConfigSnapshot s) {
        this.enableMod = s.enableMod();
        this.enableDeathTeleport = s.enableDeathTeleport();
        this.enableFreeMovement = s.enableFreeMovement();
        this.preventVoidDamage = s.preventVoidDamage();
        this.spawnX = s.spawnX();
        this.spawnY = s.spawnY();
        this.spawnZ = s.spawnZ();
        this.useSharedSpawn = s.useSharedSpawn();
        this.preventDimensionEscape = s.preventDimensionEscape();
        this.preserveInventory = s.preserveInventory();
        this.preserveExperience = s.preserveExperience();
        this.restorePlayerAbilities = s.restorePlayerAbilities();
        this.returnToDeathLocation = s.returnToDeathLocation();
        this.enableCooldown = s.enableCooldown();
        this.cooldownDuration = s.cooldownDuration();
        this.showCooldownMessage = s.showCooldownMessage();
        this.showCooldownActionbar = s.showCooldownActionbar();
        this.allowAdminBypass = s.allowAdminBypass();
        this.enableReturnSystem = s.enableReturnSystem();
        this.returnMode = s.returnMode();
        this.returnToOriginalDimension = s.returnToOriginalDimension();
        this.returnToOriginalPosition = s.returnToOriginalPosition();
        this.allowAdminReturn = s.allowAdminReturn();
        this.skipDeathScreen = s.skipDeathScreen();
        this.enableVoidParticles = s.enableVoidParticles();
        this.enableDeathTitle = s.enableDeathTitle();
        this.enableReturnTitle = s.enableReturnTitle();
        this.enableEyeOpenEffect = s.enableEyeOpenEffect();
        this.enableReviveAnimation = s.enableReviveAnimation();
        this.reviveAnimationStyle = s.reviveAnimationStyle();
    }

    @Override
    protected void init() {
        this.spawnXBox = null;
        this.spawnYBox = null;
        this.spawnZBox = null;
        this.cooldownDurationBox = null;

        // Top tab bar
        Tab[] tabs = Tab.values();
        int tabWidth = 84;
        int gap = 4;
        int totalTabW = tabs.length * tabWidth + (tabs.length - 1) * gap;
        int startX = (this.width - totalTabW) / 2;
        int tabY = 26;

        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Button tabBtn = Button.builder(Component.translatable(tab.key), b -> {
                if (commitVisibleInputs()) {
                    this.activeTab = tab;
                    this.rebuildWidgets();
                }
            }).bounds(startX + i * (tabWidth + gap), tabY, tabWidth, 20).build();
            tabBtn.active = (tab != this.activeTab);
            this.addRenderableWidget(tabBtn);
        }

        // Body layout (2 columns)
        int colW = 190;
        int colGap = 12;
        int leftX = (this.width - (colW * 2 + colGap)) / 2;
        int rightX = leftX + colW + colGap;
        int rowStartY = 60;
        int rowStep = 26;

        switch (this.activeTab) {
            case GENERAL -> {
                addToggle(leftX, rowStartY, colW, "gui.deathdimension.opt.enable_mod", enableMod, v -> enableMod = v);
                addToggle(rightX, rowStartY, colW, "gui.deathdimension.opt.enable_death_teleport", enableDeathTeleport, v -> enableDeathTeleport = v);
                addToggle(leftX, rowStartY + rowStep, colW, "gui.deathdimension.opt.enable_free_movement", enableFreeMovement, v -> enableFreeMovement = v);
                addToggle(rightX, rowStartY + rowStep, colW, "gui.deathdimension.opt.prevent_void_damage", preventVoidDamage, v -> preventVoidDamage = v);
                addToggle(leftX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.preserve_inventory", preserveInventory, v -> preserveInventory = v);
                addToggle(rightX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.preserve_experience", preserveExperience, v -> preserveExperience = v);
                addToggle(leftX, rowStartY + rowStep * 3, colW, "gui.deathdimension.opt.restore_abilities", restorePlayerAbilities, v -> restorePlayerAbilities = v);
            }
            case DIMENSION -> {
                this.spawnXBox = addNumberBox(leftX, rowStartY + 12, colW, Double.toString(spawnX));
                this.spawnYBox = addNumberBox(rightX, rowStartY + 12, colW, Double.toString(spawnY));
                this.spawnZBox = addNumberBox(leftX, rowStartY + 54, colW, Double.toString(spawnZ));
                addToggle(leftX, rowStartY + 86, colW, "gui.deathdimension.opt.use_shared_spawn", useSharedSpawn, v -> useSharedSpawn = v);
                addToggle(rightX, rowStartY + 86, colW, "gui.deathdimension.opt.prevent_escape", preventDimensionEscape, v -> preventDimensionEscape = v);
            }
            case COOLDOWN -> {
                addToggle(leftX, rowStartY, colW, "gui.deathdimension.opt.enable_cooldown", enableCooldown, v -> enableCooldown = v);
                this.cooldownDurationBox = addNumberBox(rightX, rowStartY + 12, colW, Long.toString(cooldownDuration));
                addToggle(leftX, rowStartY + rowStep + 14, colW, "gui.deathdimension.opt.show_cooldown_msg", showCooldownMessage, v -> showCooldownMessage = v);
                addToggle(rightX, rowStartY + rowStep + 14, colW, "gui.deathdimension.opt.show_cooldown_bar", showCooldownActionbar, v -> showCooldownActionbar = v);
                addToggle(leftX, rowStartY + rowStep * 2 + 14, colW, "gui.deathdimension.opt.allow_admin_bypass", allowAdminBypass, v -> allowAdminBypass = v);
            }
            case RETURN -> {
                addToggle(leftX, rowStartY, colW, "gui.deathdimension.opt.enable_return", enableReturnSystem, v -> enableReturnSystem = v);
                this.addRenderableWidget(
                        CycleButton.<ReturnMode>builder(mode -> Component.translatable(mode.translationKey()))
                                .withValues(ReturnMode.values())
                                .withInitialValue(returnMode)
                                .create(rightX, rowStartY, colW, 20,
                                        Component.translatable("gui.deathdimension.opt.return_mode"),
                                        (btn, val) -> returnMode = val)
                );
                addToggle(leftX, rowStartY + rowStep, colW, "gui.deathdimension.opt.return_orig_dim", returnToOriginalDimension, v -> returnToOriginalDimension = v);
                addToggle(rightX, rowStartY + rowStep, colW, "gui.deathdimension.opt.return_orig_pos", returnToOriginalPosition, v -> returnToOriginalPosition = v);
                addToggle(leftX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.return_death_loc", returnToDeathLocation, v -> returnToDeathLocation = v);
                addToggle(rightX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.allow_admin_return", allowAdminReturn, v -> allowAdminReturn = v);
            }
            case EFFECTS -> {
                addToggle(leftX, rowStartY, colW, "gui.deathdimension.opt.skip_death_screen", skipDeathScreen, v -> skipDeathScreen = v);
                addToggle(rightX, rowStartY, colW, "gui.deathdimension.opt.void_particles", enableVoidParticles, v -> enableVoidParticles = v);
                addToggle(leftX, rowStartY + rowStep, colW, "gui.deathdimension.opt.death_title", enableDeathTitle, v -> enableDeathTitle = v);
                addToggle(rightX, rowStartY + rowStep, colW, "gui.deathdimension.opt.return_title", enableReturnTitle, v -> enableReturnTitle = v);
                addToggle(leftX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.eye_open_effect", enableEyeOpenEffect, v -> enableEyeOpenEffect = v);
                addToggle(rightX, rowStartY + rowStep * 2, colW, "gui.deathdimension.opt.revive_animation", enableReviveAnimation, v -> enableReviveAnimation = v);
                this.addRenderableWidget(
                        CycleButton.<ReviveAnimationStyle>builder(style -> Component.translatable(style.translationKey()))
                                .withValues(ReviveAnimationStyle.values())
                                .withInitialValue(reviveAnimationStyle)
                                .create(leftX, rowStartY + rowStep * 3, colW * 2 + colGap, 20,
                                        Component.translatable("gui.deathdimension.opt.revive_style"),
                                        (btn, val) -> reviveAnimationStyle = val)
                );
            }
        }

        // Bottom Save & Cancel buttons
        int bottomY = this.height - 28;
        int btnW = 140;
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.deathdimension.config.save"), b -> onSave())
                        .bounds(this.width / 2 - btnW - 6, bottomY, btnW, 20)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                        .bounds(this.width / 2 + 6, bottomY, btnW, 20)
                        .build()
        );
    }

    private void addToggle(int x, int y, int width, String key, boolean initial, java.util.function.Consumer<Boolean> setter) {
        this.addRenderableWidget(
                CycleButton.onOffBuilder(initial)
                        .create(x, y, width, 20, Component.translatable(key), (btn, val) -> setter.accept(val))
        );
    }

    private EditBox addNumberBox(int x, int y, int width, String initial) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setValue(initial);
        box.setMaxLength(16);
        this.addRenderableWidget(box);
        return box;
    }

    private boolean commitVisibleInputs() {
        this.errorMessage = null;
        try {
            if (this.spawnXBox != null) {
                this.spawnX = Double.parseDouble(this.spawnXBox.getValue().trim());
            }
            if (this.spawnYBox != null) {
                this.spawnY = Double.parseDouble(this.spawnYBox.getValue().trim());
            }
            if (this.spawnZBox != null) {
                this.spawnZ = Double.parseDouble(this.spawnZBox.getValue().trim());
            }
            if (this.cooldownDurationBox != null) {
                long parsed = Long.parseLong(this.cooldownDurationBox.getValue().trim());
                if (parsed < 0L) {
                    throw new NumberFormatException("negative");
                }
                this.cooldownDuration = parsed;
            }
            return true;
        } catch (NumberFormatException ex) {
            this.errorMessage = Component.translatable("gui.deathdimension.config.invalid_number").withStyle(ChatFormatting.RED);
            return false;
        }
    }

    private void onSave() {
        if (!commitVisibleInputs()) {
            return;
        }
        ModConfig.ConfigSnapshot snapshot = new ModConfig.ConfigSnapshot(
                enableMod, enableDeathTeleport, enableFreeMovement, preventVoidDamage,
                spawnX, spawnY, spawnZ, useSharedSpawn, preventDimensionEscape,
                preserveInventory, preserveExperience, restorePlayerAbilities, returnToDeathLocation,
                enableCooldown, cooldownDuration, showCooldownMessage, showCooldownActionbar, allowAdminBypass,
                enableReturnSystem, returnMode, returnToOriginalDimension, returnToOriginalPosition, allowAdminReturn,
                skipDeathScreen, enableVoidParticles, enableDeathTitle, enableReturnTitle,
                enableEyeOpenEffect, enableReviveAnimation, reviveAnimationStyle
        ).sanitized();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new ConfigUpdatePayload(snapshot));
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int colW = 190;
        int colGap = 12;
        int leftX = (this.width - (colW * 2 + colGap)) / 2;
        int rightX = leftX + colW + colGap;
        int rowStartY = 60;

        if (this.activeTab == Tab.DIMENSION) {
            guiGraphics.drawString(this.font, Component.translatable("gui.deathdimension.opt.spawn_x"), leftX, rowStartY, 0xCCCCCC);
            guiGraphics.drawString(this.font, Component.translatable("gui.deathdimension.opt.spawn_y"), rightX, rowStartY, 0xCCCCCC);
            guiGraphics.drawString(this.font, Component.translatable("gui.deathdimension.opt.spawn_z"), leftX, rowStartY + 42, 0xCCCCCC);
        } else if (this.activeTab == Tab.COOLDOWN) {
            guiGraphics.drawString(this.font, Component.translatable("gui.deathdimension.opt.cooldown_duration"), rightX, rowStartY, 0xCCCCCC);
        }

        if (this.errorMessage != null) {
            guiGraphics.drawCenteredString(this.font, this.errorMessage, this.width / 2, this.height - 44, 0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private static final class OpDeniedScreen extends Screen {
        @Nullable
        private final Screen parent;

        OpDeniedScreen(@Nullable Screen parent) {
            super(Component.translatable("gui.deathdimension.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                            .bounds(this.width / 2 - 75, this.height / 2 + 24, 150, 20)
                            .build()
            );
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 36, 0xFFFFFF);
            guiGraphics.drawCenteredString(
                    this.font,
                    Component.translatable("gui.deathdimension.config.op_only_line1").withStyle(ChatFormatting.RED),
                    this.width / 2, this.height / 2 - 12, 0xFF5555
            );
            guiGraphics.drawCenteredString(
                    this.font,
                    Component.translatable("gui.deathdimension.config.op_only_line2").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 + 2, 0xAAAAAA
            );
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(this.parent);
        }
    }
}
