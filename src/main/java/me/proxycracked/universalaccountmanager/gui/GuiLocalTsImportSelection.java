package me.proxycracked.universalaccountmanager.gui;

import java.util.ArrayList;
import java.util.List;
import me.proxycracked.universalaccountmanager.gui.GuiLocalTsStore.ImportCandidate;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiLocalTsImportSelection extends GuiScreen {
    private static final int PAGE_SIZE = 5;

    private final GuiLocalTsStore parent;
    private final List<ImportCandidate> candidates;
    private final boolean[] selected;
    private int page;
    private String status = "Select the accounts to add to your list.";
    private GuiButton importButton;
    private GuiButton backButton;

    public GuiLocalTsImportSelection(GuiLocalTsStore parent, List<ImportCandidate> candidates) {
        this.parent = parent;
        this.candidates = new ArrayList<>(candidates);
        this.selected = new boolean[candidates.size()];
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int start = page * PAGE_SIZE;
        int end = Math.min(candidates.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            String label = candidateLabel(index);
            buttonList.add(new GuiButton(100 + index - start, centerX - 152, 36 + (index - start) * 22, 304, 20, label));
        }
        buttonList.add(new GuiButton(1, centerX - 152, 148, 148, 20, "Previous Page"));
        buttonList.add(new GuiButton(2, centerX + 4, 148, 148, 20, "Next Page"));
        buttonList.add(new GuiButton(3, centerX - 152, 172, 148, 20, "Select All"));
        buttonList.add(new GuiButton(4, centerX + 4, 172, 148, 20, "Select None"));
        buttonList.add(importButton = new GuiButton(5, centerX - 152, 196, 200, 20, "Import Selected"));
        buttonList.add(backButton = new GuiButton(6, centerX + 52, 196, 100, 20, "Back"));
        updateButtons();
    }

    private String candidateLabel(int index) {
        ImportCandidate candidate = candidates.get(index);
        String prefix = selected[index] ? "[x] " : "[ ] ";
        String suffix = "  (order " + candidate.getOrderId() + ")";
        return fontRendererObj.trimStringToWidth(prefix + candidate.getLabel() + suffix, 294);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Import Localts Accounts", width / 2, 14, 0xFFFFFF);
        drawCenteredString(fontRendererObj,
            selectedCount() + " selected  |  Page " + (page + 1) + "/" + pageCount(),
            width / 2, 25, 0xAAAAAA);
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(status, Math.max(100, width - 20)), width / 2, 220, 0xDDDDDD);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) actionPerformed(backButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;
        if (button.id >= 100 && button.id < 100 + PAGE_SIZE) {
            int index = page * PAGE_SIZE + button.id - 100;
            if (index < candidates.size()) {
                selected[index] = !selected[index];
                button.displayString = candidateLabel(index);
                updateButtons();
            }
            return;
        }
        switch (button.id) {
            case 1:
                page--;
                initGui();
                break;
            case 2:
                page++;
                initGui();
                break;
            case 3:
                for (int i = 0; i < selected.length; i++) selected[i] = true;
                initGui();
                break;
            case 4:
                for (int i = 0; i < selected.length; i++) selected[i] = false;
                initGui();
                break;
            case 5:
                List<ImportCandidate> chosen = selectedCandidates();
                if (chosen.isEmpty()) {
                    status = "Select at least one account first.";
                } else {
                    mc.displayGuiScreen(parent);
                    parent.importSelectedPurchases(chosen);
                }
                break;
            case 6:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void updateButtons() {
        int pages = pageCount();
        for (GuiButton button : buttonList) {
            if (button.id == 1) button.enabled = page > 0;
            if (button.id == 2) button.enabled = page + 1 < pages;
        }
        if (importButton != null) importButton.enabled = selectedCount() > 0;
    }

    private int pageCount() {
        return Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int selectedCount() {
        int count = 0;
        for (boolean value : selected) if (value) count++;
        return count;
    }

    private List<ImportCandidate> selectedCandidates() {
        List<ImportCandidate> result = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (selected[i]) result.add(candidates.get(i));
        }
        return result;
    }
}
