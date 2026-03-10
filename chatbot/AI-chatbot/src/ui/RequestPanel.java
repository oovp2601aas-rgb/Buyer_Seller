package ui;

import controller.ChatController;
import java.awt.*;
import javax.swing.*;
import model.ChatRequest;

/**
 * RequestPanel - Form per request di dashboard seller
 * - 3 form field per seller (Form 1, Form 2, Form 3)
* - Each has its own submit button
 */
public class RequestPanel extends JPanel {
    private ChatRequest    request;
    private ChatController controller;
    private int            sellerIndex;

    private JTextArea[] formFields = new JTextArea[3]; // index 0=Form1, 1=Form2, 2=Form3

    private static final String[] FORM_LABELS = {
        "Form 1", "Form 2", "Form 3"
    };

    public RequestPanel(ChatRequest request, ChatController controller, int sellerIndex) {
        this.request     = request;
        this.controller  = controller;
        this.sellerIndex = sellerIndex;
        initComponents();
    }

    public RequestPanel(ChatRequest request, ChatController controller) {
        this(request, controller, 0);
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)));

        setPreferredSize(new Dimension(450, 340));
        setMinimumSize(new Dimension(400, 340));

        // Request label
        JPanel lp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        lp.setBackground(Color.WHITE);
        lp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        JLabel rl = new JLabel(request.getRequestLabel());
        rl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lp.add(rl);
        add(lp);

        add(Box.createVerticalStrut(4));

        // Buyer message
        JPanel mp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        mp.setBackground(Color.WHITE);
        mp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        JLabel ml = new JLabel(request.getBuyerMessage());
        ml.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ml.setForeground(new Color(60, 60, 60));
        mp.add(ml);
        add(mp);

        add(Box.createVerticalStrut(10));

        // 3 form rows
        for (int i = 0; i < 3; i++) {
            add(createFormRow(i));
            add(Box.createVerticalStrut(6));
        }
    }

    private JPanel createFormRow(int idx) {
        int formNumber = idx + 1; // 1, 2, 3

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(Color.WHITE);
        row.setPreferredSize(new Dimension(420, 70));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        // Label "Form 1" / "Form 2" / "Form 3"
        JLabel fl = new JLabel(FORM_LABELS[idx]);
        fl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fl.setPreferredSize(new Dimension(55, 32));
        fl.setForeground(new Color(130, 130, 130));
        row.add(fl, BorderLayout.WEST);

        // TextArea
        formFields[idx] = new JTextArea();
        formFields[idx].setFont(new Font("Segoe UI", Font.PLAIN, 13));
        formFields[idx].setBackground(new Color(245, 255, 245));
        formFields[idx].setLineWrap(true);
        formFields[idx].setWrapStyleWord(true);
        formFields[idx].setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JScrollPane sp = new JScrollPane(formFields[idx]);
        sp.setBorder(BorderFactory.createLineBorder(new Color(210, 235, 210), 1, true));
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        row.add(sp, BorderLayout.CENTER);

        // Submit button
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        bp.setBackground(Color.WHITE);

        CircularButton sub = new CircularButton("➤", new Color(0, 150, 136), 32);
        sub.setToolTipText("Submit Form " + formNumber);
        final int fi = formNumber;
        sub.addActionListener(e -> handleSubmit(fi));
        bp.add(sub);

        row.add(bp, BorderLayout.EAST);
        return row;
    }

    private void handleSubmit(int formIndex) {
        int idx = formIndex - 1; // 0-based
        if (idx < 0 || idx >= formFields.length) return;
        String v = formFields[idx].getText().trim();
        if (!v.isEmpty()) {
            controller.onSellerFormSubmit(request.getRequestId(), formIndex, v, sellerIndex);
        }
    }

    /** Fill a specific form field (1-based) */
    public void fillForm(int formIndex, String value) {
        int idx = formIndex - 1;
        if (idx >= 0 && idx < formFields.length && formFields[idx] != null) {
            formFields[idx].setText(value);
        }
    }

    public ChatRequest getRequest() { return request; }

    public void updateRequest(ChatRequest r) {
        this.request = r;
        if (r.getProductExplanation() != null
                && formFields[0] != null
                && formFields[0].getText().isEmpty()) {
            formFields[0].setText(r.getProductExplanation());
        }
    }
}