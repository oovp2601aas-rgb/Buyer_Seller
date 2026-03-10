package ui;

import controller.ChatController;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import model.RecommendationItem;

/**
 * RecommendationPanel – Window "Recommendations for Buyers"
 *
 * Displays all menu items sent by sellers (from form submit),
 * grouped by seller. Each item has a checkbox.
 * When buyer checks → item appears in BuyerPanel as final selection bubble.
 *
 * Layout per item:
 *   [ Seller Name badge ]
 *   [ ☐  Menu Name   Qty stepper   Price   ]
 */
public class RecommendationPanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────
    private static final Color BG          = Color.WHITE;
    private static final Color HDR_BG      = new Color(74, 20, 140);   // deep purple
    private static final Color CARD_BG     = new Color(250, 248, 255);
    private static final Color CARD_BORDER = new Color(200, 180, 240);
    private static final Color SELLER_COLORS[] = {
        new Color(33, 150, 243),   // blue
        new Color(0,  150, 136),   // teal
        new Color(233, 30,  99),   // pink
    };
    private static final Color CHECKED_BG  = new Color(232, 245, 233);
    private static final Color CHECKED_BDR = new Color(129, 199, 132);

    // ── State ─────────────────────────────────────────────────
    private ChatController           controller;
    private List<RecommendationItem> items        = new ArrayList<>();
    private Map<String, ItemCard>    cards        = new LinkedHashMap<>();
    // requestIds that have been paid
    private java.util.Set<Integer>   doneRequests = new java.util.LinkedHashSet<>();
    // requestId order so groups appear in order
    private List<Integer>            requestOrder = new ArrayList<>();

    // ── Layout ────────────────────────────────────────────────
    private JPanel      listPanel;
    private JLabel      emptyLabel;
    private JScrollPane scrollPane;
    private JLabel      counterLabel;

    public RecommendationPanel() {
        initComponents();
    }

    public void setController(ChatController c) {
        this.controller = c;
    }

    // ──────────────────────────────────────────────────────────
    //  Build UI
    // ──────────────────────────────────────────────────────────
    private void initComponents() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        // ── Header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HDR_BG);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("🍽️ Recommendations for Buyers", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI Emoji", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        counterLabel = new JLabel("0 items");
        counterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        counterLabel.setForeground(new Color(200, 180, 240));

        header.add(title,        BorderLayout.CENTER);
        header.add(counterLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── List panel ──
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BG);
        listPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        emptyLabel = new JLabel("No recommendations from sellers yet.", SwingConstants.CENTER);
        emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        emptyLabel.setForeground(new Color(160, 160, 160));
        emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        listPanel.add(emptyLabel);

        scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // ── Footer hint ──
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(new Color(245, 240, 255));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER));
        JLabel hint = new JLabel("Click Choose to select — your selection will appear in Buyer Chat");
        hint.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        hint.setForeground(new Color(103, 58, 183));
        footer.add(hint);
        add(footer, BorderLayout.SOUTH);
    }

    // ──────────────────────────────────────────────────────────
    //  Public API — called from ChatController
    // ──────────────────────────────────────────────────────────

    /**
     * Add one recommendation item from a seller.
     * Called each time a seller submits the form.
     */
    public void addRecommendation(RecommendationItem item) {
        items.add(item);
        if (!requestOrder.contains(item.getRequestId())) {
            requestOrder.add(item.getRequestId());
        }
        String key = item.getRequestId() + "-" + item.getSellerIndex() + "-" + items.size();
        ItemCard card = new ItemCard(item, key);
        cards.put(key, card);
        rebuildList();
    }

    /**
     * Called when buyer finishes payment.
     * Locks all active requestIds as Done so that
     * the next order appears as a new group below.
     */
    public void markCurrentOrderDone() {
        doneRequests.addAll(requestOrder);
        rebuildList();
    }

    /** Remove all (called when clearAllChats) */
    public void clearAll() {
        items.clear();
        cards.clear();
        doneRequests.clear();
        requestOrder.clear();
        rebuildList();
    }

    // ──────────────────────────────────────────────────────────
    //  Rebuild list — grouped by seller
    // ──────────────────────────────────────────────────────────
    private void rebuildList() {
        listPanel.removeAll();

        if (cards.isEmpty()) {
            listPanel.add(emptyLabel);
            counterLabel.setText("0 items");
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }

        counterLabel.setText(cards.size() + " item" + (cards.size() > 1 ? "s" : ""));

        // ── Group by requestId (order session), then by seller ──
        // requestOrder is already sorted by arrival order
        Map<Integer, Map<Integer, List<ItemCard>>> grouped = new LinkedHashMap<>();
        for (Integer rid : requestOrder) {
            grouped.put(rid, new LinkedHashMap<>());
        }
        for (ItemCard c : cards.values()) {
            int rid = c.item.getRequestId();
            int sid = c.item.getSellerIndex();
            grouped.computeIfAbsent(rid, k -> new LinkedHashMap<>())
                   .computeIfAbsent(sid, k -> new ArrayList<>())
                   .add(c);
        }

        int orderNum = 1;
        for (Map.Entry<Integer, Map<Integer, List<ItemCard>>> orderEntry : grouped.entrySet()) {
            int rid      = orderEntry.getKey();
            boolean done = doneRequests.contains(rid);

            // ── Order header ──
            JPanel orderHeader = new JPanel(new BorderLayout(10, 0));
            orderHeader.setBackground(done ? new Color(232, 245, 233) : new Color(237, 231, 246));
            orderHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(done ? new Color(129,199,132) : new Color(179,136,255), 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)));
            orderHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

            JLabel orderLbl = new JLabel("Order #" + orderNum);
            orderLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            orderLbl.setForeground(done ? new Color(27, 94, 32) : new Color(74, 20, 140));
            orderHeader.add(orderLbl, BorderLayout.WEST);

            if (done) {
                JLabel doneLbl = new JLabel("✅ Done");
                doneLbl.setFont(new Font("Segoe UI Emoji", Font.BOLD, 12));
                doneLbl.setForeground(new Color(27, 94, 32));
                orderHeader.add(doneLbl, BorderLayout.EAST);
            } else {
                JLabel activeLbl = new JLabel("⏳ Active");
                activeLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
                activeLbl.setForeground(new Color(103, 58, 183));
                orderHeader.add(activeLbl, BorderLayout.EAST);
            }

            listPanel.add(orderHeader);
            listPanel.add(Box.createVerticalStrut(6));

            // ── Group by seller within this order ──
            for (Map.Entry<Integer, List<ItemCard>> sellerEntry : orderEntry.getValue().entrySet()) {
                int sellerIdx    = sellerEntry.getKey();
                List<ItemCard> sellerCards = sellerEntry.getValue();

                // Seller badge
                JPanel sellerHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
                sellerHeader.setBackground(BG);
                sellerHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

                Color sc = sellerIdx < SELLER_COLORS.length ? SELLER_COLORS[sellerIdx] : new Color(100,100,100);
                String sellerName = sellerCards.get(0).item.getSellerName();

                JLabel badge = new JLabel("  " + sellerName + "  ");
                badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
                badge.setForeground(Color.WHITE);
                badge.setBackground(sc);
                badge.setOpaque(true);
                badge.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(sc, 10),
                        BorderFactory.createEmptyBorder(3, 10, 3, 10)));
                sellerHeader.add(badge);
                listPanel.add(sellerHeader);
                listPanel.add(Box.createVerticalStrut(3));

                for (ItemCard card : sellerCards) {
                    // Disable card if order is already done
                    card.setEnabled(!done);
                    listPanel.add(card);
                    listPanel.add(Box.createVerticalStrut(5));
                }
            }

            listPanel.add(Box.createVerticalStrut(14));
            orderNum++;
        }

        listPanel.revalidate();
        listPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = scrollPane.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    // ──────────────────────────────────────────────────────────
    //  ItemCard — one row per menu item
    // ──────────────────────────────────────────────────────────
    private class ItemCard extends JPanel {

        final RecommendationItem item;
        final String             key;
        private JButton          chooseBtn;
        private JLabel           qtyLabel;
        private JLabel           priceLabel;
        private int              qty;
        private boolean          chosen = false;

        ItemCard(RecommendationItem item, String key) {
            this.item = item;
            this.key  = key;
            this.qty  = Math.max(1, item.getQuantity());
            buildUI();
        }

        private void buildUI() {
            setLayout(new BorderLayout(10, 0));
            setBackground(CARD_BG);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setPreferredSize(new Dimension(460, 58));
            setBorder(BorderFactory.createCompoundBorder(
                    new RoundBorder(CARD_BORDER, 12),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)));

            // ── Left: menu name + price ──
            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);

            String displayName = extractMenuName(item.getRawMessage());
            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            nameLabel.setForeground(new Color(40, 40, 40));
            left.add(nameLabel);

            double price = item.getUnitPrice();
            if (price > 0) {
                priceLabel = new JLabel(formatRp(price * qty));
                priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                priceLabel.setForeground(new Color(27, 94, 32));
                left.add(priceLabel);
            }

            // ── Middle: qty stepper ──
            JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            middle.setOpaque(false);

            JButton minusBtn = stepBtn("−");
            qtyLabel = new JLabel(String.valueOf(qty));
            qtyLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            qtyLabel.setForeground(new Color(33, 37, 41));
            qtyLabel.setPreferredSize(new Dimension(26, 28));
            qtyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            JButton plusBtn = stepBtn("+");

            minusBtn.addActionListener(e -> { if (qty > 1) { qty--; refreshQty(); } });
            plusBtn.addActionListener(e -> { qty++; refreshQty(); });

            middle.add(minusBtn);
            middle.add(qtyLabel);
            middle.add(plusBtn);

            // ── Right: Choose button ──
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);

            chooseBtn = new JButton("Choose");
            chooseBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            chooseBtn.setForeground(new Color(0, 150, 136));
            chooseBtn.setBackground(Color.WHITE);
            chooseBtn.setFocusPainted(false);
            chooseBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chooseBtn.setBorder(BorderFactory.createCompoundBorder(
                    new RoundBorder(new Color(0, 150, 136), 15),
                    BorderFactory.createEmptyBorder(5, 14, 5, 14)));

            chooseBtn.addActionListener(e -> {
                if (!chosen) {
                    chosen = true;
                    updateVisual();
                    if (controller != null) {
                        item.setQuantity(qty);
                        controller.onRecommendationChosen(item, qty);
                    }
                } else {
                    chosen = false;
                    updateVisual();
                    if (controller != null) {
                        controller.onRecommendationUnchosen(item);
                    }
                }
            });

            right.add(chooseBtn);

            // ── Combine: left | middle | right ──
            JPanel centerRight = new JPanel(new BorderLayout(8, 0));
            centerRight.setOpaque(false);
            centerRight.add(middle, BorderLayout.WEST);
            centerRight.add(right,  BorderLayout.EAST);

            add(left,        BorderLayout.CENTER);
            add(centerRight, BorderLayout.EAST);
        }

        private void refreshQty() {
            qtyLabel.setText(String.valueOf(qty));
            if (priceLabel != null && item.getUnitPrice() > 0) {
                priceLabel.setText(formatRp(item.getUnitPrice() * qty));
            }
            revalidate(); repaint();
        }

        private JButton stepBtn(String text) {
            JButton b = new JButton(text);
            b.setFont(new Font("Segoe UI", Font.BOLD, 13));
            b.setPreferredSize(new Dimension(26, 26));
            b.setFocusPainted(false);
            b.setBackground(new Color(240, 240, 240));
            b.setBorder(BorderFactory.createCompoundBorder(
                    new RoundBorder(new Color(180, 180, 180), 6),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return b;
        }

        private void updateVisual() {
            setBackground(chosen ? CHECKED_BG : CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new RoundBorder(chosen ? CHECKED_BDR : CARD_BORDER, 12),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)));

            Color darkGreen = new Color(27, 94, 32);
            if (chosen) {
                chooseBtn.setText("\u2705 Chosen");
                chooseBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
                chooseBtn.setForeground(darkGreen);
                chooseBtn.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(darkGreen, 15),
                        BorderFactory.createEmptyBorder(5, 14, 5, 14)));
            } else {
                chooseBtn.setText("Choose");
                chooseBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
                chooseBtn.setForeground(new Color(0, 150, 136));
                chooseBtn.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(new Color(0, 150, 136), 15),
                        BorderFactory.createEmptyBorder(5, 14, 5, 14)));
            }
            repaint();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────
    private String extractMenuName(String raw) {
        if (raw == null || raw.isEmpty()) return "(no name)";
        // Strip "[SellerX - Form N] " prefix if present
        String s = raw.trim();
        if (s.startsWith("[") && s.contains("]")) {
            s = s.substring(s.indexOf(']') + 1).trim();
        }
        // First line only, max 60 chars
        String line = s.split("\n")[0].trim();
        return line.length() > 60 ? line.substring(0, 57) + "…" : line;
    }

    private String formatRp(double amount) {
        if (amount <= 0) return "";
        long val = Math.round(amount);
        String raw = String.valueOf(val);
        StringBuilder sb = new StringBuilder();
        int start = raw.length() % 3;
        if (start > 0) sb.append(raw, 0, start);
        for (int i = start; i < raw.length(); i += 3) {
            if (sb.length() > 0) sb.append(".");
            sb.append(raw, i, i + 3);
        }
        return "Rp " + sb;
    }

    // ──────────────────────────────────────────────────────────
    //  RoundBorder helper
    // ──────────────────────────────────────────────────────────
    static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int   radius;
        RoundBorder(Color c, int r) { this.color = c; this.radius = r; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(4, 4, 4, 4); }
    }
}