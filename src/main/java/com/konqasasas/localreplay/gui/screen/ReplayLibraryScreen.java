package com.konqasasas.localreplay.gui.screen;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.gui.GuiContainer;
import com.konqasasas.localreplay.gui.component.Button;
import com.konqasasas.localreplay.gui.component.Panel;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;
import com.konqasasas.localreplay.replay.ReplayController;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.io.ReplayFileException;
import com.konqasasas.localreplay.replay.io.ReplayFileIO;
import com.konqasasas.localreplay.replay.io.ReplayFileSummary;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local .lreplay browser. The library never mutates replay contents: OPEN
 * performs a strict full load, while list rows use only the validated header.
 */
public final class ReplayLibraryScreen extends GuiScreen {

    private static final long DOUBLE_CLICK_MS = 275L;

    private final ReplayController controller;
    private final GuiScreen returnScreen;
    private final GuiContainer ui = new GuiContainer();
    private final List<LibraryEntry> entries = new ArrayList<LibraryEntry>();

    private Path replayDirectory;

    private int tableX;
    private int tableY;
    private int tableWidth;
    private int tableHeight;
    private int headerHeight;
    private int rowHeight;
    private int visibleRows;
    private int scrollOffset;
    private int pageLeft;
    private int pageWidth;
    private int actionsY;
    private int actionsHeight;
    private int actionSeparatorX;
    private int selectedIndex = -1;

    private Button openButton;
    private Button renameButton;
    private Button deleteButton;
    private Button openFolderButton;
    private Button settingsButton;
    private Button closeButton;

    private GuiTextField renameField;
    private LibraryEntry renameEntry;
    private LibraryEntry pendingDelete;
    private Button modalPrimaryButton;
    private Button modalCancelButton;

    private String statusText = "";
    private int statusColor = LocalReplayTheme.TEXT_MUTED;
    private long statusUntilMillis;

    private int lastClickIndex = -1;
    private long lastClickMillis;
    private UiScaleContext uiScale;
    private int uiWidth;
    private int uiHeight;

    public ReplayLibraryScreen(ReplayController controller, GuiScreen returnScreen) {
        if (controller == null) throw new IllegalArgumentException("controller cannot be null");
        this.controller = controller;
        this.returnScreen = returnScreen;
    }

    @Override
    public void initGui() {
        super.initGui();

        replayDirectory = LocalReplayMod.getPaths() == null
                ? null
                : LocalReplayMod.getPaths().getReplaysDirectory();
        uiScale = UiScaleContext.forScreen(mc, width, height);
        uiWidth = uiScale.getVirtualWidth();
        uiHeight = uiScale.getVirtualHeight();

        int outer = u(26);
        pageWidth = Math.min(Math.max(u(620), 320), Math.max(320, uiWidth - outer * 2));
        pageLeft = (uiWidth - pageWidth) / 2;
        int titleHeight = Math.max(u(44), 24);
        int top = u(24);
        int gap = Math.max(2, u(10));
        headerHeight = Math.max(u(38), 20);
        rowHeight = Math.max(u(46), 24);
        actionsHeight = Math.max(u(36), 18);
        int bottomPad = u(22);
        int statusHeight = Math.max(u(20), 10);

        tableX = pageLeft;
        tableY = top + titleHeight;
        tableWidth = pageWidth;
        actionsY = uiHeight - bottomPad - actionsHeight;
        tableHeight = Math.max(headerHeight + rowHeight,
                actionsY - statusHeight - gap - tableY);
        visibleRows = Math.max(1, (tableHeight - headerHeight) / rowHeight);

        Panel root = new Panel(0, 0, uiWidth, uiHeight, 0x00000000, 0x00000000);
        root.setBorder(false);

        int actionGap = Math.max(2, u(7));
        int openW = Math.max(u(70), 38);
        int renameW = Math.max(u(82), 42);
        int deleteW = Math.max(u(76), 40);
        int folderW = Math.max(u(112), 58);
        int settingsW = Math.max(u(92), 48);
        int closeW = Math.max(u(72), 38);

        int x = pageLeft;
        openButton = new Button(x, actionsY, openW, actionsHeight, "OPEN", this::openSelected);
        openButton.setStyle(Button.Style.PRIMARY);
        root.addChild(openButton);
        x += openW + actionGap;

        renameButton = new Button(x, actionsY, renameW, actionsHeight, "RENAME", this::startRename);
        root.addChild(renameButton);
        x += renameW + actionGap;

        deleteButton = new Button(x, actionsY, deleteW, actionsHeight, "DELETE", this::startDelete);
        deleteButton.setStyle(Button.Style.DANGER);
        root.addChild(deleteButton);
        x += deleteW + actionGap;

        actionSeparatorX = x + u(4);
        x = actionSeparatorX + u(10);
        openFolderButton = new Button(x, actionsY, folderW, actionsHeight, "OPEN FOLDER", this::openFolder);
        root.addChild(openFolderButton);

        int right = pageLeft + pageWidth;
        closeButton = new Button(right - closeW, actionsY, closeW, actionsHeight, "CLOSE", this::closeLibrary);
        root.addChild(closeButton);
        right -= closeW + actionGap;
        settingsButton = new Button(right - settingsW, actionsY, settingsW, actionsHeight, "SETTINGS", this::openSettings);
        root.addChild(settingsButton);

        ui.setRoot(root);

        // A resize should not leave a half-open modal using stale coordinates.
        renameField = null;
        renameEntry = null;
        pendingDelete = null;
        modalPrimaryButton = null;
        modalCancelButton = null;

        refreshEntries(null);
        updateButtonState();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (renameField != null) renameField.updateCursorCounter();
        if (statusUntilMillis > 0L && System.currentTimeMillis() >= statusUntilMillis) {
            statusUntilMillis = 0L;
            statusText = "";
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (renameField != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelModal();
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitRename();
                return;
            }
            renameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }

        if (pendingDelete != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelModal();
            } else if (keyCode == Keyboard.KEY_RETURN
                    || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_DELETE) {
                confirmDelete();
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeLibrary();
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            moveSelection(-1);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            moveSelection(1);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            openSelected();
            return;
        }
        if (keyCode == Keyboard.KEY_F2) {
            startRename();
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            startDelete();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (uiScale == null) return;
        mouseX = uiScale.toDesignX(mouseX);
        mouseY = uiScale.toDesignY(mouseY);
        if (renameField != null) {
            renameField.mouseClicked(mouseX, mouseY, mouseButton);
            if (modalPrimaryButton != null && modalPrimaryButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (modalCancelButton != null) modalCancelButton.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (pendingDelete != null) {
            if (modalPrimaryButton != null && modalPrimaryButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (modalCancelButton != null) modalCancelButton.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0 && isInsideRows(mouseX, mouseY)) {
            int row = (mouseY - (tableY + headerHeight)) / rowHeight;
            int index = scrollOffset + row;
            if (index >= 0 && index < entries.size()) {
                selectedIndex = index;
                ensureSelectionVisible();
                updateButtonState();

                long now = System.currentTimeMillis();
                if (lastClickIndex == index && now - lastClickMillis <= DOUBLE_CLICK_MS) {
                    openSelected();
                }
                lastClickIndex = index;
                lastClickMillis = now;
                return;
            }
        }

        ui.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (uiScale == null) return;
        mouseX = uiScale.toDesignX(mouseX);
        mouseY = uiScale.toDesignY(mouseY);
        if (modalPrimaryButton != null) modalPrimaryButton.mouseReleased(mouseX, mouseY, state);
        if (modalCancelButton != null) modalCancelButton.mouseReleased(mouseX, mouseY, state);
        if (renameField == null && pendingDelete == null) ui.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (renameField != null || pendingDelete != null) return;

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || entries.size() <= visibleRows) return;

        if (uiScale == null) return;
        int mx = uiScale.toDesignX(mouseX());
        int my = uiScale.toDesignY(mouseY());
        if (mx < tableX || mx >= tableX + tableWidth || my < tableY || my >= tableY + tableHeight) return;

        int direction = wheel > 0 ? -1 : 1;
        scrollOffset = clamp(scrollOffset + direction * 3, 0, maxScrollOffset());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (uiScale == null) return;
        TrueTypeRenderer tr = LocalReplayMod.getTrueTypeRenderer();
        int ux = uiScale.toDesignX(mouseX);
        int uy = uiScale.toDesignY(mouseY);
        uiScale.begin();
        try {
            Gui.drawRect(0, 0, uiWidth, uiHeight, LocalReplayTheme.CANVAS);

        int top = u(24);
        tr.drawString("Replay Library", pageLeft, top, LocalReplayTheme.TEXT_STRONG, FontRole.UI_SEMIBOLD);
        String count = entries.size() + (entries.size() == 1 ? " replay" : " replays");
        int countW = tr.getStringWidth(count, FontRole.MONO);
        tr.drawString(count, pageLeft + pageWidth - countW, top, LocalReplayTheme.TEXT_MUTED, FontRole.MONO);

        drawTable(tr, ux, uy);
        drawStatus(tr);

        if (actionSeparatorX > pageLeft) {
            int sy = actionsY + u(5);
            ModernUiDraw.verticalLine(actionSeparatorX, sy, actionsY + actionsHeight - u(5), LocalReplayTheme.LINE);
        }
        ui.render(tr, ux, uy, partialTicks);

        if (renameField != null) {
            drawRenameModal(tr, ux, uy, partialTicks);
        } else if (pendingDelete != null) {
            drawDeleteModal(tr, ux, uy, partialTicks);
        }
        } finally {
            uiScale.end();
        }
    }

    private void drawTable(TrueTypeRenderer tr, int mouseX, int mouseY) {
        int radius = Math.max(3, u(9));
        ModernUiDraw.roundedBorder(
                tableX, tableY, tableX + tableWidth, tableY + tableHeight,
                radius, LocalReplayTheme.LINE, LocalReplayTheme.SURFACE
        );

        int nameW = Math.max(1, (int) Math.floor(tableWidth * 0.52D));
        int durationW = Math.max(1, (int) Math.floor(tableWidth * 0.16D));
        int ticksW = Math.max(1, (int) Math.floor(tableWidth * 0.14D));
        int mcW = Math.max(1, tableWidth - nameW - durationW - ticksW);

        int nameX = tableX;
        int durationX = nameX + nameW;
        int ticksX = durationX + durationW;
        int mcX = ticksX + ticksW;

        // Header is intentionally subtle; no spreadsheet-style vertical grid.
        Gui.drawRect(tableX + 1, tableY + radius, tableX + tableWidth - 1,
                tableY + headerHeight, LocalReplayTheme.SURFACE_SUBTLE);
        ModernUiDraw.horizontalLine(
                tableX + 1, tableX + tableWidth - 1, tableY + headerHeight,
                LocalReplayTheme.LINE
        );
        drawHeaderText(tr, "FILENAME", nameX, nameW, false);
        drawHeaderText(tr, "DURATION", durationX, durationW, true);
        drawHeaderText(tr, "TICKS", ticksX, ticksW, true);
        drawHeaderText(tr, "MC VERSION", mcX, mcW, true);

        if (entries.isEmpty()) {
            tr.drawCenteredString(
                    "No replays",
                    tableX + tableWidth * 0.5F,
                    tableY + headerHeight + (tableHeight - headerHeight) * 0.5F,
                    LocalReplayTheme.TEXT_FAINT,
                    FontRole.UI
            );
            return;
        }

        int firstY = tableY + headerHeight;
        int end = Math.min(entries.size(), scrollOffset + visibleRows);
        for (int index = scrollOffset; index < end; index++) {
            int row = index - scrollOffset;
            int y = firstY + row * rowHeight;
            LibraryEntry entry = entries.get(index);
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= tableX && mouseX < tableX + tableWidth
                    && mouseY >= y && mouseY < y + rowHeight;

            if (selected) {
                Gui.drawRect(tableX + 1, y, tableX + tableWidth - 1, y + rowHeight,
                        LocalReplayTheme.SURFACE_SELECTED);
                Gui.drawRect(tableX + 1, y, tableX + Math.max(3, u(4)), y + rowHeight,
                        LocalReplayTheme.FOCUS);
            } else if (hovered) {
                Gui.drawRect(tableX + 1, y, tableX + tableWidth - 1, y + rowHeight,
                        LocalReplayTheme.SURFACE_RAISED);
            }
            ModernUiDraw.horizontalLine(
                    tableX + 1, tableX + tableWidth - 1, y + rowHeight,
                    LocalReplayTheme.LINE
            );

            int textColor = entry.isValid() ? LocalReplayTheme.TEXT : LocalReplayTheme.GOAL;
            String fileName = ellipsize(tr, entry.getFileName(), Math.max(1, nameW - u(18)), FontRole.UI);
            drawRowLeft(tr, fileName, nameX + u(10), y, rowHeight, textColor, FontRole.UI);

            if (entry.isValid()) {
                drawRowCentered(tr, formatDuration(entry.summary.getTickCount()), durationX, durationW, y, rowHeight,
                        LocalReplayTheme.TEXT_MUTED, FontRole.MONO);
                drawRowCentered(tr, Integer.toString(entry.summary.getTickCount()), ticksX, ticksW, y, rowHeight,
                        LocalReplayTheme.TEXT_MUTED, FontRole.MONO);
                drawRowCentered(tr, entry.summary.getMinecraftVersion(), mcX, mcW, y, rowHeight,
                        LocalReplayTheme.TEXT_MUTED, FontRole.MONO);
            } else {
                drawRowCentered(tr, "INVALID", durationX, durationW, y, rowHeight, LocalReplayTheme.GOAL, FontRole.UI);
                drawRowCentered(tr, "--", ticksX, ticksW, y, rowHeight, LocalReplayTheme.TEXT_FAINT, FontRole.MONO);
                drawRowCentered(tr, "--", mcX, mcW, y, rowHeight, LocalReplayTheme.TEXT_FAINT, FontRole.MONO);
            }
        }
    }

    private void drawHeaderText(TrueTypeRenderer tr, String text, int x, int w, boolean centered) {
        if (centered) {
            tr.drawCenteredString(text, x + w * 0.5F, tableY + headerHeight * 0.5F,
                    LocalReplayTheme.TEXT_FAINT, FontRole.UI);
        } else {
            tr.drawString(text, x + u(10), tableY + Math.max(1, (headerHeight - tr.getLineHeight(FontRole.UI)) / 2),
                    LocalReplayTheme.TEXT_FAINT, FontRole.UI);
        }
    }

    private static void drawRowCentered(
            TrueTypeRenderer tr,
            String text,
            int x,
            int w,
            int y,
            int h,
            int color,
            FontRole role
    ) {
        tr.drawCenteredString(text, x + w * 0.5F, y + h * 0.5F, color, role);
    }

    private static void drawRowLeft(
            TrueTypeRenderer tr,
            String text,
            int x,
            int y,
            int h,
            int color,
            FontRole role
    ) {
        tr.drawString(text, x, y + Math.max(1, (h - tr.getLineHeight(role)) / 2), color, role);
    }

    private void drawStatus(TrueTypeRenderer tr) {
        String text = statusText;
        int color = statusColor;
        if (text.isEmpty() && selectedIndex >= 0 && selectedIndex < entries.size()) {
            LibraryEntry selected = entries.get(selectedIndex);
            if (!selected.isValid()) {
                text = "INVALID REPLAY FILE";
                color = LocalReplayTheme.GOAL;
            }
        }
        if (text.isEmpty()) return;

        int y = tableY + tableHeight + u(7);
        tr.drawString(text, tableX, y, color, FontRole.UI);
    }

    private void drawRenameModal(TrueTypeRenderer tr, int mouseX, int mouseY, float partialTicks) {
        int modalW = Math.min(Math.max(u(360), 180), Math.max(180, uiWidth - u(40)));
        int modalH = Math.max(u(130), 80);
        int left = (uiWidth - modalW) / 2;
        int top = (uiHeight - modalH) / 2;
        drawModalShell(left, top, modalW, modalH);

        tr.drawString("Rename replay", left + u(16), top + u(16), LocalReplayTheme.TEXT_STRONG, FontRole.UI_SEMIBOLD);
        tr.drawString("Filename", left + u(16), top + u(42), LocalReplayTheme.TEXT_MUTED, FontRole.UI);
        int fieldX = left + u(14);
        int fieldY = top + u(56);
        int fieldW = modalW - u(28);
        int fieldH = Math.max(u(20), 12);
        ModernUiDraw.roundedBorder(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH,
                Math.max(2, u(5)), LocalReplayTheme.LINE_CONTROL, LocalReplayTheme.SURFACE_SUBTLE);
        renameField.drawTextBox();

        if (modalPrimaryButton != null) modalPrimaryButton.render(tr, mouseX, mouseY, partialTicks);
        if (modalCancelButton != null) modalCancelButton.render(tr, mouseX, mouseY, partialTicks);
    }

    private void drawDeleteModal(TrueTypeRenderer tr, int mouseX, int mouseY, float partialTicks) {
        int modalW = Math.min(Math.max(u(390), 200), Math.max(200, uiWidth - u(40)));
        int modalH = Math.max(u(120), 76);
        int left = (uiWidth - modalW) / 2;
        int top = (uiHeight - modalH) / 2;
        drawModalShell(left, top, modalW, modalH);

        tr.drawString("Delete replay?", left + u(16), top + u(16), LocalReplayTheme.TEXT_STRONG, FontRole.UI_SEMIBOLD);
        String name = pendingDelete == null ? "" : pendingDelete.getFileName();
        name = ellipsize(tr, name, modalW - u(28), FontRole.UI);
        tr.drawString(name, left + u(14), top + u(40), LocalReplayTheme.TEXT_MUTED, FontRole.UI);

        if (modalPrimaryButton != null) modalPrimaryButton.render(tr, mouseX, mouseY, partialTicks);
        if (modalCancelButton != null) modalCancelButton.render(tr, mouseX, mouseY, partialTicks);
    }

    private void drawModalShell(int left, int top, int w, int h) {
        Gui.drawRect(0, 0, uiWidth, uiHeight, LocalReplayTheme.MODAL_BACKDROP);
        ModernUiDraw.roundedBorder(left, top, left + w, top + h, Math.max(3, u(9)),
                LocalReplayTheme.LINE_STRONG, LocalReplayTheme.SURFACE);
    }

    private void refreshEntries(Path preferSelection) {
        entries.clear();
        if (replayDirectory == null) {
            selectedIndex = -1;
            setStatus("REPLAY DIRECTORY UNAVAILABLE", true);
            return;
        }

        try {
            Files.createDirectories(replayDirectory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(replayDirectory)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path) || !isReplayFileName(path.getFileName().toString())) continue;
                    try {
                        entries.add(new LibraryEntry(path, ReplayFileIO.readSummary(path), null));
                    } catch (ReplayFileException exception) {
                        entries.add(new LibraryEntry(path, null, exception.getMessage()));
                    }
                }
            }
        } catch (IOException exception) {
            setStatus("FAILED TO READ REPLAY DIRECTORY", true);
            logError("Failed to read Replay Library directory.", exception);
        }

        Collections.sort(entries, new Comparator<LibraryEntry>() {
            @Override
            public int compare(LibraryEntry a, LibraryEntry b) {
                long at = a.summary == null ? lastModifiedQuietly(a.path) : a.summary.getCreatedAtEpochMillis();
                long bt = b.summary == null ? lastModifiedQuietly(b.path) : b.summary.getCreatedAtEpochMillis();
                int byTime = Long.compare(bt, at);
                if (byTime != 0) return byTime;
                return b.getFileName().compareToIgnoreCase(a.getFileName());
            }
        });

        selectedIndex = -1;
        if (preferSelection != null) {
            Path normalized = preferSelection.toAbsolutePath().normalize();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).path.equals(normalized)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex < 0 && !entries.isEmpty()) selectedIndex = 0;
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset());
        ensureSelectionVisible();
        updateButtonState();
    }

    private void openSelected() {
        LibraryEntry entry = selectedEntry();
        if (entry == null) return;
        if (!entry.isValid()) {
            setStatus("INVALID REPLAY FILE", true);
            return;
        }
        if (mc.world == null || mc.player == null) {
            setStatus("ENTER A WORLD BEFORE OPENING A REPLAY", true);
            return;
        }

        try {
            ReplayData data = ReplayFileIO.load(entry.path);
            if (!controller.openFileReplay(data, this)) {
                setStatus("COULD NOT START REPLAY", true);
                return;
            }
            mc.displayGuiScreen(new ReplayScreen(controller));
        } catch (ReplayFileException exception) {
            setStatus("FAILED TO OPEN REPLAY", true);
            logError("Failed to open replay: " + entry.path, exception);
            refreshEntries(entry.path);
        }
    }

    private void startRename() {
        LibraryEntry entry = selectedEntry();
        if (entry == null) return;
        renameEntry = entry;
        pendingDelete = null;

        int modalW = Math.min(Math.max(u(360), 180), Math.max(180, uiWidth - u(40)));
        int modalH = Math.max(u(130), 80);
        int left = (uiWidth - modalW) / 2;
        int top = (uiHeight - modalH) / 2;
        int fieldX = left + u(14);
        int fieldY = top + u(56);
        int fieldW = modalW - u(28);
        int fieldH = Math.max(u(20), 12);

        renameField = new GuiTextField(1001, mc.fontRenderer, fieldX, fieldY, fieldW, fieldH);
        renameField.setMaxStringLength(120);
        renameField.setEnableBackgroundDrawing(false);
        renameField.setText(stripReplayExtension(entry.getFileName()));
        renameField.setFocused(true);

        int buttonH = Math.max(u(24), 13);
        int buttonW = Math.max(u(72), 38);
        int gap = u(6);
        int buttonY = top + modalH - u(14) - buttonH;
        int right = left + modalW - u(14);
        modalCancelButton = new Button(right - buttonW, buttonY, buttonW, buttonH, "CANCEL", this::cancelModal);
        modalPrimaryButton = new Button(right - buttonW * 2 - gap, buttonY, buttonW, buttonH, "RENAME", this::commitRename);
        modalPrimaryButton.setStyle(Button.Style.PRIMARY);
    }

    private void commitRename() {
        if (renameField == null || renameEntry == null || replayDirectory == null) return;
        String requested = renameField.getText() == null ? "" : renameField.getText().trim();
        requested = stripReplayExtension(requested);
        if (!isValidBaseName(requested)) {
            setStatus("INVALID FILENAME", true);
            return;
        }

        Path destination = replayDirectory.resolve(requested + ReplayFileIO.FILE_EXTENSION).toAbsolutePath().normalize();
        Path source = renameEntry.path;
        if (!destination.getParent().equals(replayDirectory.toAbsolutePath().normalize())) {
            setStatus("INVALID FILENAME", true);
            return;
        }
        if (destination.equals(source)) {
            cancelModal();
            return;
        }
        if (Files.exists(destination)) {
            setStatus("A REPLAY WITH THAT NAME ALREADY EXISTS", true);
            return;
        }

        try {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(source, destination);
            }
            cancelModal();
            refreshEntries(destination);
            setStatus("RENAMED", false);
        } catch (IOException exception) {
            setStatus("FAILED TO RENAME REPLAY", true);
            logError("Failed to rename replay.", exception);
        }
    }

    private void startDelete() {
        LibraryEntry entry = selectedEntry();
        if (entry == null) return;
        pendingDelete = entry;
        renameField = null;
        renameEntry = null;

        int modalW = Math.min(Math.max(u(390), 200), Math.max(200, uiWidth - u(40)));
        int modalH = Math.max(u(120), 76);
        int left = (uiWidth - modalW) / 2;
        int top = (uiHeight - modalH) / 2;
        int buttonH = Math.max(u(24), 13);
        int buttonW = Math.max(u(72), 38);
        int gap = u(6);
        int buttonY = top + modalH - u(14) - buttonH;
        int right = left + modalW - u(14);
        modalCancelButton = new Button(right - buttonW, buttonY, buttonW, buttonH, "CANCEL", this::cancelModal);
        modalPrimaryButton = new Button(right - buttonW * 2 - gap, buttonY, buttonW, buttonH, "DELETE", this::confirmDelete);
        modalPrimaryButton.setStyle(Button.Style.DANGER);
    }

    private void confirmDelete() {
        if (pendingDelete == null) return;
        Path deletePath = pendingDelete.path;
        try {
            Files.deleteIfExists(deletePath);
            cancelModal();
            refreshEntries(null);
            setStatus("DELETED", false);
        } catch (IOException exception) {
            setStatus("FAILED TO DELETE REPLAY", true);
            logError("Failed to delete replay.", exception);
        }
    }

    private void cancelModal() {
        renameField = null;
        renameEntry = null;
        pendingDelete = null;
        modalPrimaryButton = null;
        modalCancelButton = null;
    }

    private void openFolder() {
        if (replayDirectory == null) {
            setStatus("REPLAY DIRECTORY UNAVAILABLE", true);
            return;
        }
        try {
            Files.createDirectories(replayDirectory);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(replayDirectory.toFile());
            } else {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("explorer", replayDirectory.toAbsolutePath().toString()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", replayDirectory.toAbsolutePath().toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", replayDirectory.toAbsolutePath().toString()).start();
                }
            }
        } catch (Exception exception) {
            setStatus("FAILED TO OPEN FOLDER", true);
            logError("Failed to open replay folder.", exception);
        }
    }

    private void openSettings() {
        mc.displayGuiScreen(new ReplaySettingsScreen(this));
    }

    private void closeLibrary() {
        mc.displayGuiScreen(returnScreen);
    }

    private void moveSelection(int delta) {
        if (entries.isEmpty()) {
            selectedIndex = -1;
            updateButtonState();
            return;
        }
        if (selectedIndex < 0) selectedIndex = 0;
        else selectedIndex = clamp(selectedIndex + delta, 0, entries.size() - 1);
        ensureSelectionVisible();
        updateButtonState();
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) return;
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + visibleRows) {
            scrollOffset = selectedIndex - visibleRows + 1;
        }
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset());
    }

    private int maxScrollOffset() {
        return Math.max(0, entries.size() - Math.max(1, visibleRows));
    }

    private void updateButtonState() {
        LibraryEntry selected = selectedEntry();
        if (openButton != null) openButton.setEnabled(selected != null && selected.isValid());
        if (renameButton != null) renameButton.setEnabled(selected != null);
        if (deleteButton != null) deleteButton.setEnabled(selected != null);
        if (openFolderButton != null) openFolderButton.setEnabled(replayDirectory != null);
        if (settingsButton != null) settingsButton.setEnabled(true);
        if (closeButton != null) closeButton.setEnabled(true);
    }

    private LibraryEntry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    private boolean isInsideRows(int mouseX, int mouseY) {
        return mouseX >= tableX && mouseX < tableX + tableWidth
                && mouseY >= tableY + headerHeight && mouseY < tableY + tableHeight;
    }

    private void setStatus(String text, boolean error) {
        statusText = text == null ? "" : text;
        statusColor = error ? LocalReplayTheme.GOAL : LocalReplayTheme.TEXT_MUTED;
        statusUntilMillis = System.currentTimeMillis() + 2500L;
    }

    private int u(int physicalPixels) {
        return UiScaleContext.fromPhysical(physicalPixels);
    }

    private int mouseX() {
        return Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
    }

    private int mouseY() {
        return height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
    }

    private static String formatDuration(int ticks) {
        long millis = Math.max(0L, ticks * 50L);
        long hours = millis / 3_600_000L;
        long minutes = (millis / 60_000L) % 60L;
        long seconds = (millis / 1_000L) % 60L;
        long ms = millis % 1_000L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        }
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private static String stripReplayExtension(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(ReplayFileIO.FILE_EXTENSION)) {
            return name.substring(0, name.length() - ReplayFileIO.FILE_EXTENSION.length());
        }
        return name;
    }

    private static boolean isReplayFileName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(ReplayFileIO.FILE_EXTENSION);
    }

    private static boolean isValidBaseName(String name) {
        if (name == null || name.isEmpty() || name.length() > 120) return false;
        if (".".equals(name) || "..".equals(name)) return false;
        if (name.endsWith(" ") || name.endsWith(".")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == '<' || c == '>' || c == ':' || c == '"'
                    || c == '/' || c == '\\' || c == '|' || c == '?' || c == '*') {
                return false;
            }
        }
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return false;
        return true;
    }

    private static String ellipsize(TrueTypeRenderer tr, String text, int maxWidth, FontRole role) {
        if (text == null) return "";
        if (tr.getStringWidth(text, role) <= maxWidth) return text;
        String suffix = "...";
        int suffixW = tr.getStringWidth(suffix, role);
        if (suffixW >= maxWidth) return suffix;
        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end) + suffix;
            if (tr.getStringWidth(candidate, role) <= maxWidth) return candidate;
            end--;
        }
        return suffix;
    }

    private static long lastModifiedQuietly(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void logError(String message, Throwable throwable) {
        if (LocalReplayMod.getLogger() != null) {
            LocalReplayMod.getLogger().error(message, throwable);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class LibraryEntry {
        final Path path;
        final ReplayFileSummary summary;
        final String error;

        LibraryEntry(Path path, ReplayFileSummary summary, String error) {
            this.path = path.toAbsolutePath().normalize();
            this.summary = summary;
            this.error = error;
        }

        boolean isValid() {
            return summary != null;
        }

        String getFileName() {
            Path name = path.getFileName();
            return name == null ? path.toString() : name.toString();
        }
    }
}
