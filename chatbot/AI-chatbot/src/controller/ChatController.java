package controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Timer;
import model.ChatRequest;
import model.RecommendationItem;
import service.SellerAIService;
import ui.BuyerPanel;
import ui.RecommendationPanel;
import ui.SellerPanel;

public class ChatController {

    private BuyerPanel              buyerPanel;
    private List<SellerPanel>       sellerPanels        = new ArrayList<>();
    private RecommendationPanel     recommendationPanel; // ← NEW
    private List<ChatRequest>       activeRequests;
    private int                     requestIdCounter;

    // KEY: requestId-sellerIndex-message.hashCode()
    private Map<String, CartItem>   cart                = new LinkedHashMap<>();

    // Set of seller indices chosen by the buyer (for WhatsApp button)
    private Set<Integer>            chosenSellerIndices = new LinkedHashSet<>();

    private SellerAIService aiService;

    // ── CartItem ──────────────────────────────────────────────
    public static class CartItem {
        public String message;
        public int    quantity;
        public double unitPrice;
        public int    sellerIndex;

        CartItem(String msg, int qty, double price, int sellerIdx) {
            this.message     = msg;
            this.quantity    = qty;
            this.unitPrice   = price;
            this.sellerIndex = sellerIdx;
        }
    }

    public ChatController() {
        this.activeRequests   = new ArrayList<>();
        this.requestIdCounter = 1;
        this.aiService        = new SellerAIService();
    }

    public void setBuyerPanel(BuyerPanel bp) {
        this.buyerPanel = bp;
        bp.setController(this);
    }

    public void addSellerPanel(SellerPanel sp) {
        sellerPanels.add(sp);
        sp.setController(this);
    }

    /** Register the RecommendationPanel window */
    public void setRecommendationPanel(RecommendationPanel rp) {
        this.recommendationPanel = rp;
        rp.setController(this);
    }

    // ──────────────────────────────────────────────────────────
    //  Buyer sends a message
    // ──────────────────────────────────────────────────────────
    public void onBuyerMessageSent(String message) {
        ChatRequest request = new ChatRequest(requestIdCounter++, message);
        request.setStatus(ChatRequest.Status.WAITING);
        activeRequests.add(request);

        buyerPanel.displayBuyerMessage(message);

        for (int i = 0; i < sellerPanels.size(); i++) {
            final int sellerIdx = i;
            String sellerName   = sellerPanels.get(i).getSellerName();
            Timer t = new Timer(400 + sellerIdx * 80, e ->
                buyerPanel.displayWaitingForSeller(request.getRequestId(), sellerIdx, sellerName)
            );
            t.setRepeats(false);
            t.start();
        }

        for (SellerPanel sp : sellerPanels) {
            sp.addRequest(request);
        }

        System.out.println("[ChatController] Broadcast REQ-" + request.getRequestId()
                + " to " + sellerPanels.size() + " sellers: " + message);
    }

    // ──────────────────────────────────────────────────────────
    //  Seller submits a form field
    // ──────────────────────────────────────────────────────────
    public void onSellerFormSubmit(int requestId, int formIndex, String value, int sellerIndex) {
        ChatRequest request = findRequestById(requestId);
        if (request == null) return;

        switch (formIndex) {
            case 1: request.setProductExplanation(value); break;
            case 2: request.setPriceEstimation(value);    break;
            case 3: request.setStockAvailability(value);  break;
        }

        String label       = getSellerName(sellerIndex);
        String displayText = "[" + label + " - Form " + formIndex + "] " + value;
        buyerPanel.replaceWaitingBubble(requestId, sellerIndex, formIndex, displayText);

        // ── NEW: push to RecommendationPanel ──────────────────
        if (recommendationPanel != null) {
            double price = parsePrice(value);

            // Find qty from buyer message, match keyword with seller value
            int qty = 1;
            if (request != null) {
                java.util.Map<String, Integer> qtyMap = parseBuyerQtyMap(request.getBuyerMessage());
                String valueLower = value.toLowerCase();
                for (java.util.Map.Entry<String, Integer> entry : qtyMap.entrySet()) {
                    if (valueLower.contains(entry.getKey()) || entry.getKey().contains(valueLower.split("\\s+")[0])) {
                        qty = entry.getValue();
                        break;
                    }
                }
                // Fallback: take the largest qty found in the buyer message
                if (qty == 1 && !qtyMap.isEmpty()) {
                    qty = qtyMap.values().iterator().next();
                }
            }

            String sellerName = getSellerName(sellerIndex);
            RecommendationItem recItem = new RecommendationItem(
                    requestId, sellerIndex, sellerName,
                    value, price, qty, displayText);
            recommendationPanel.addRecommendation(recItem);
        }

        System.out.println("[ChatController] " + label + " submitted REQ-" + requestId + " Form-" + formIndex);
    }

    public void onSellerFormSubmit(int requestId, int formIndex, String value) {
        onSellerFormSubmit(requestId, formIndex, value, 0);
    }

    // ──────────────────────────────────────────────────────────
    //  AI suggestion
    // ──────────────────────────────────────────────────────────
    public void onAISuggestRequested(int requestId, int formIndex, int sellerIndex) {
        ChatRequest request = findRequestById(requestId);
        if (request == null) return;

        SellerAIService.ResponseType type;
        switch (formIndex) {
            case 2:  type = SellerAIService.ResponseType.PRICE_ESTIMATION;    break;
            case 3:  type = SellerAIService.ResponseType.STOCK_AVAILABILITY;  break;
            default: type = SellerAIService.ResponseType.PRODUCT_EXPLANATION; break;
        }

        String suggestion = aiService.generateResponse(request.getBuyerMessage(), type, requestId);
        if (sellerIndex >= 0 && sellerIndex < sellerPanels.size()) {
            sellerPanels.get(sellerIndex).fillFormField(requestId, formIndex, suggestion);
        }
    }

    public void onAISuggestRequested(int requestId, int formIndex) {
        onAISuggestRequested(requestId, formIndex, 0);
    }

    // ──────────────────────────────────────────────────────────
    //  Buyer ticks a checkbox in RecommendationPanel
    // ──────────────────────────────────────────────────────────
    public void onRecommendationChosen(RecommendationItem item, int quantity) {
        // Add to cart
        String key = "rec-" + item.getRequestId() + "-" + item.getSellerIndex()
                   + "-" + item.getRawMessage().hashCode();
        cart.put(key, new CartItem(item.getRawMessage(), quantity,
                                   item.getUnitPrice(), item.getSellerIndex()));
        chosenSellerIndices.add(item.getSellerIndex());

        // Show as chosen bubble in BuyerPanel
        buyerPanel.displayRecommendationChosen(item, quantity);

        refreshSummary();
    }

    /** Buyer un-ticks a checkbox — remove from cart */
    public void onRecommendationUnchosen(RecommendationItem item) {
        String key = "rec-" + item.getRequestId() + "-" + item.getSellerIndex()
                   + "-" + item.getRawMessage().hashCode();
        cart.remove(key);

        // Recalculate chosen sellers
        chosenSellerIndices.clear();
        for (CartItem ci : cart.values()) {
            chosenSellerIndices.add(ci.sellerIndex);
        }

        buyerPanel.removeRecommendationChosen(item);
        refreshSummary();
    }

    // ──────────────────────────────────────────────────────────
    //  Buyer clicks Choose (existing bubble choose button)
    // ──────────────────────────────────────────────────────────
    public void onBuyerChoose(int requestId, int sellerIndex, String message, int quantity, double unitPrice) {
        String key = requestId + "-" + sellerIndex + "-" + message.hashCode();
        cart.put(key, new CartItem(message, quantity, unitPrice, sellerIndex));
        chosenSellerIndices.add(sellerIndex);
        refreshSummary();
    }

    public void onBuyerChoose(int requestId, int sellerIndex, String message, int quantity) {
        onBuyerChoose(requestId, sellerIndex, message, quantity, 0.0);
    }

    public void onBuyerChoose(int requestId, int sellerIndex, String message) {
        onBuyerChoose(requestId, sellerIndex, message, 1, 0.0);
    }

    // ──────────────────────────────────────────────────────────
    //  Summary
    // ──────────────────────────────────────────────────────────
    private void refreshSummary() {
        if (buyerPanel == null || cart.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83E\uDDFE Order Summary\n");
        sb.append("--------------------------\n\n");

        String address = buyerPanel.getAddress();
        if (!address.isEmpty()) {
            sb.append("\uD83D\uDCCD Address: ").append(address).append("\n\n");
        }

        double  grandTotal = 0;
        boolean hasPrice   = false;

        for (CartItem item : cart.values()) {
            String title = item.message.split("\n")[0].trim();
            if (title.startsWith("[") && title.contains("]")) {
                title = title.substring(title.indexOf("]") + 1).trim();
            }
            sb.append("• ").append(title).append("  x").append(item.quantity);

            if (item.unitPrice > 0) {
                double sub = item.unitPrice * item.quantity;
                grandTotal += sub;
                hasPrice = true;
                sb.append("  =  ").append(formatRupiah(sub));
            }
            sb.append("\n\n");
        }

        sb.append("--------------------------\n");
        if (hasPrice) {
            sb.append("Grand Total: ").append(formatRupiah(grandTotal)).append("\n\n");
        }
        sb.append("Please confirm your order \uD83D\uDE0A");

        buyerPanel.displayBuyerSummary(sb.toString(), grandTotal, buildChosenSellersInfo());
    }

    public List<String[]> buildChosenSellersInfo() {
        List<String[]> result = new ArrayList<>();

        for (int sellerIdx : chosenSellerIndices) {
            String sellerName = getSellerName(sellerIdx);
            String phone      = getSellerPhone(sellerIdx);

            StringBuilder items = new StringBuilder();
            double sellerTotal  = 0;
            for (CartItem item : cart.values()) {
                if (item.sellerIndex == sellerIdx) {
                    String title = item.message.split("\n")[0].trim();
                    if (title.startsWith("[") && title.contains("]")) {
                        title = title.substring(title.indexOf("]") + 1).trim();
                    }
                    items.append("• ").append(title)
                         .append(" x").append(item.quantity);
                    if (item.unitPrice > 0) {
                        double sub = item.unitPrice * item.quantity;
                        sellerTotal += sub;
                        items.append(" = ").append(formatRupiah(sub));
                    }
                    items.append("\n");
                }
            }

            String totalStr = sellerTotal > 0 ? formatRupiah(sellerTotal) : "-";
            result.add(new String[]{ sellerName, phone, items.toString(), totalStr });
        }

        return result;
    }

    private String getSellerPhone(int sellerIndex) {
        String[] phones = {
            "6285708223820",
            "6289514366861",
            "6285894121730",
        };
        if (sellerIndex >= 0 && sellerIndex < phones.length) return phones[sellerIndex];
        return "";
    }

    public void clearCart() {
        cart.clear();
        chosenSellerIndices.clear();
        if (buyerPanel != null) buyerPanel.clearBuyerSummary();
        if (recommendationPanel != null) recommendationPanel.markCurrentOrderDone();
    }

    // ──────────────────────────────────────────────────────────
    //  Clear all
    // ──────────────────────────────────────────────────────────
    public void clearAllChats() {
        activeRequests.clear();
        cart.clear();
        chosenSellerIndices.clear();
        requestIdCounter = 1;
        if (buyerPanel != null) buyerPanel.clearChat();
        for (SellerPanel sp : sellerPanels) sp.clearAllRequests();
        if (recommendationPanel != null) recommendationPanel.clearAll();
        System.out.println("[ChatController] All chats cleared");
    }

    // ──────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────
    private String getSellerName(int idx) {
        if (idx >= 0 && idx < sellerPanels.size()) return sellerPanels.get(idx).getSellerName();
        return "Seller " + (idx + 1);
    }

    private ChatRequest findRequestById(int id) {
        for (ChatRequest r : activeRequests) {
            if (r.getRequestId() == id) return r;
        }
        return null;
    }

    public List<ChatRequest> getActiveRequests() { return new ArrayList<>(activeRequests); }

    /** Parse the first Rp / 'k' price from a text */
    private double parsePrice(String msg) {
        if (msg == null) return 0;
        java.util.regex.Matcher rp = java.util.regex.Pattern
                .compile("(?i)rp\\.?\\s*([\\d.,]+)").matcher(msg);
        if (rp.find()) {
            try { return Double.parseDouble(rp.group(1).replace(".", "").replace(",", "")); }
            catch (NumberFormatException ignored) {}
        }
        java.util.regex.Matcher k = java.util.regex.Pattern
                .compile("(\\d+(?:[.,]\\d+)?)\\s*(?:k|rb|ribu)").matcher(msg.toLowerCase());
        if (k.find()) {
            try { return Double.parseDouble(k.group(1).replace(",", ".")) * 1000; }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private int parseQuantity(String msg) {
        if (msg == null) return 1;
        java.util.regex.Matcher mx = java.util.regex.Pattern.compile("(\\d+)\\s*[xX×]").matcher(msg);
        if (mx.find()) {
            try { return Integer.parseInt(mx.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    /**
     * Parse quantity from buyer message based on keywords.
     * "sweet 5, salty 2"        → {sweet:5, salty:2}
     * "nasi padang 3, es teh 2" → {nasi padang:3, es teh:2}
     */
    public java.util.Map<String, Integer> parseBuyerQtyMap(String message) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (message == null || message.isEmpty()) return result;
        // Format: "keyword number" separated by comma/semicolon/end-of-line
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([a-zA-Z][a-zA-Z ]{0,30}?)\\s+(\\d+)(?=\\s*[,;]|\\s*$)")
                .matcher(message.trim());
        while (m.find()) {
            String keyword = m.group(1).trim().toLowerCase();
            int qty = Integer.parseInt(m.group(2));
            if (!keyword.isEmpty()) result.put(keyword, qty);
        }
        // Fallback: "number keyword"
        if (result.isEmpty()) {
            java.util.regex.Matcher m2 = java.util.regex.Pattern
                    .compile("(\\d+)\\s+([a-zA-Z][a-zA-Z ]{0,30}?)(?=\\s*[,;]|\\s*$)")
                    .matcher(message.trim());
            while (m2.find()) {
                int qty = Integer.parseInt(m2.group(1));
                String keyword = m2.group(2).trim().toLowerCase();
                if (!keyword.isEmpty()) result.put(keyword, qty);
            }
        }
        return result;
    }

    private String formatRupiah(double amount) {
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
}