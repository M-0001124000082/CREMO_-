/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.playstion;

/**
 *
 * @author Mega Store
 */
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PLAYSTION extends JFrame {

    private static final Color BG_DARK = new Color(11, 15, 25);
    private static final Color CARD_BG = new Color(17, 24, 39);
    private static final Color CARD_BORDER = new Color(31, 41, 55);
    private static final Color ACCENT_CYAN = new Color(0, 240, 255);
    private static final Color ACCENT_BLUE = new Color(0, 112, 209);
    private static final Color ACCENT_GOLD = new Color(245, 158, 11);
    private static final Color STATUS_FREE = new Color(16, 185, 129);
    private static final Color STATUS_BUSY = new Color(239, 68, 68);
    private static final Color TEXT_WHITE = new Color(243, 244, 246);
    private static final Color TEXT_MUTED = new Color(156, 163, 175);

    // مسار قاعدة بيانات SQLite
    private static final String DB_URL = "jdbc:sqlite:PlayStation Lounge.db";

    // قوائم البيانات في الذاكرة لربط الواجهات
    private List<Room> rooms = new ArrayList<>();
    private List<MenuItem> menuItems = new ArrayList<>();
    private List<ShiftRecord> shiftHistory = new ArrayList<>();

    // متغيرات الوردية الحالية
    private int currentShiftId = -1;
    private String currentShiftType = "صباحي ☀️";
    private LocalDateTime currentShiftStartTime = LocalDateTime.now();
    private double currentShiftTimeEarnings = 0.0;
    private double currentShiftOrdersEarnings = 0.0;
    private int currentShiftSessionsCount = 0;

    // كلمة المرور الافتراضية
    private String adminPassword = "0";

    // مكونات الواجهة
    private JPanel gridPanel;
    private JLabel totalEarningsLabel;
    private JLabel activeRoomsLabel;
    private double totalDailyEarnings = 0.0;

    public PLAYSTION() {
        setTitle("نظام إدارة صالة البلايستيشن ");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmExitWithPassword();
            }
        });
        setSize(1300, 850);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(15, 15));

        // 1. تهيئة وقراءة قاعدة بيانات SQLite
        initDatabase();
        loadSettingsFromDB();
        loadRoomsFromDB();
        loadMenuFromDB();
        loadOrStartActiveShift();
        loadShiftHistoryFromDB();

        // 2. بناء الواجهات الرئيسية
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createMainGridPanel(), BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);

        // 3. مؤقت تحديث العدادات والتوقيت المباشر كل ثانية
        Timer uiRefreshTimer = new Timer(1000, e -> updateAllRoomCards());
        uiRefreshTimer.start();
    }

    private void confirmExitWithPassword() {
        JPasswordField pf = new JPasswordField();
        int option = JOptionPane.showConfirmDialog(
                this,
                new Object[]{"⚠️ إغلاق البرنامج يتطلب إدخال كلمة مرور الأدمن:", pf},
                "تأكيد إغلاق التطبيق",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            String inputPassword = new String(pf.getPassword());
            if (inputPassword.equals(adminPassword)) {
                System.exit(0); // إغلاق التطبيق بنجاح
            } else {
                JOptionPane.showMessageDialog(this, "كلمة المرور غير صحيحة! لا يمكن إغلاق التطبيق.", "خطأ في الصلاحيات", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    public static class Employee {

        private int id;
        private String name;
        private String phone;
        private String role;
        private String pinCode;

        public Employee(int id, String name, String phone, String role, String pinCode) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.role = role;
            this.pinCode = pinCode;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        public String getRole() {
            return role;
        }

        public String getPinCode() {
            return pinCode;
        }
    }

    private void initDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. جدول الغرف
            // 7. جدول الموظفين
            stmt.execute("CREATE TABLE IF NOT EXISTS employees ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "phone TEXT, "
                    + "role TEXT DEFAULT 'كاشير', "
                    + "pin_code TEXT NOT NULL UNIQUE, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

// تحديث جدول الورديات لربطه بالموظف
            try {
                stmt.execute("ALTER TABLE shifts ADD COLUMN employee_name TEXT DEFAULT 'غير محدد';");
            } catch (SQLException ignored) {
                // العمود موجود مسبقاً
            }
            stmt.execute("CREATE TABLE IF NOT EXISTS rooms ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "single_rate REAL NOT NULL, "
                    + "multi_rate REAL NOT NULL, "
                    + "is_vip INTEGER DEFAULT 0, "
                    + "is_occupied INTEGER DEFAULT 0, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

            // 2. جدول المنيو
            stmt.execute("CREATE TABLE IF NOT EXISTS menu_items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE, "
                    + "price REAL NOT NULL, "
                    + "is_available INTEGER DEFAULT 1, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

            // 3. جدول الورديات
            stmt.execute("CREATE TABLE IF NOT EXISTS shifts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "shift_type TEXT NOT NULL, "
                    + "start_time DATETIME NOT NULL, "
                    + "end_time DATETIME, "
                    + "time_earnings REAL DEFAULT 0.0, "
                    + "orders_earnings REAL DEFAULT 0.0, "
                    + "total_earnings REAL DEFAULT 0.0, "
                    + "sessions_count INTEGER DEFAULT 0, "
                    + "is_closed INTEGER DEFAULT 0);");

            // 4. جدول الجلسات
            stmt.execute("CREATE TABLE IF NOT EXISTS sessions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "room_id INTEGER NOT NULL, "
                    + "shift_id INTEGER, "
                    + "play_type TEXT CHECK(play_type IN ('SINGLE', 'MULTI')), "
                    + "start_time DATETIME NOT NULL, "
                    + "end_time DATETIME, "
                    + "duration_minutes INTEGER DEFAULT 0, "
                    + "time_cost REAL DEFAULT 0.0, "
                    + "orders_cost REAL DEFAULT 0.0, "
                    + "total_cost REAL DEFAULT 0.0, "
                    + "status TEXT DEFAULT 'ACTIVE', "
                    + "FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (shift_id) REFERENCES shifts(id) ON DELETE SET NULL);");

            // 5. جدول تفاصيل الطلبات
            stmt.execute("CREATE TABLE IF NOT EXISTS session_orders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "session_id INTEGER NOT NULL, "
                    + "item_name TEXT NOT NULL, "
                    + "unit_price REAL NOT NULL, "
                    + "quantity INTEGER NOT NULL DEFAULT 1, "
                    + "total_price REAL NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE);");

            // 6. جدول إعدادات النظام
            stmt.execute("CREATE TABLE IF NOT EXISTS system_settings ("
                    + "setting_key TEXT PRIMARY KEY, "
                    + "setting_value TEXT NOT NULL, "
                    + "description TEXT);");

            // إدراج البيانات الافتراضية في حال كانت الداتابيز جديدة
            seedDefaultDatabaseData(conn);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "خطأ في الاتصال بقاعدة البيانات SQLite: " + e.getMessage(), "خطأ قاعدة البيانات", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seedDefaultDatabaseData(Connection conn) throws SQLException {
        // إدراج كلمة المرور الافتراضية
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT OR IGNORE INTO system_settings (setting_key, setting_value, description) VALUES ('admin_password', '1234', 'كلمة مرور الأدمن');");

            // لا نضيف غرف افتراضية - سيضيفها المستخدم يدويًا
        }
    }

    private void loadSettingsFromDB() {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key = 'admin_password'")) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                adminPassword = rs.getString("setting_value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadRoomsFromDB() {
        rooms.clear();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM rooms ORDER BY id ASC")) {

            while (rs.next()) {
                Room r = new Room(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("single_rate"),
                        rs.getDouble("multi_rate"),
                        rs.getInt("is_vip") == 1
                );

                // الاستعلام عما إذا كانت الغرفة بها جلسة نشطة
                loadActiveSessionForRoom(conn, r);
                rooms.add(r);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadActiveSessionForRoom(Connection conn, Room room) throws SQLException {
        String sql = "SELECT * FROM sessions WHERE room_id = ? AND status = 'ACTIVE' LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, room.getId());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int sessionId = rs.getInt("id");
                boolean isMulti = "MULTI".equalsIgnoreCase(rs.getString("play_type"));
                String startStr = rs.getString("start_time");
                LocalDateTime startTime = LocalDateTime.parse(startStr.replace(" ", "T"));

                room.restoreActiveSession(sessionId, isMulti, startTime);

                // تحميل طلبات الجلسة النشطة
                loadOrdersForSession(conn, room, sessionId);
            }
        } catch (Exception ex) {
            // التعامل مع صِيَغ التواريخ المخزنة
        }
    }

    private void loadOrdersForSession(Connection conn, Room room, int sessionId) throws SQLException {
        String sql = "SELECT item_name, unit_price, quantity FROM session_orders WHERE session_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                room.addOrderInMemory(rs.getString("item_name"), rs.getDouble("unit_price"), rs.getInt("quantity"));
            }
        }
    }

    private void loadMenuFromDB() {
        menuItems.clear();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM menu_items WHERE is_available = 1")) {

            while (rs.next()) {
                menuItems.add(new MenuItem(rs.getInt("id"), rs.getString("name"), rs.getDouble("price")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadOrStartActiveShift() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM shifts WHERE is_closed = 0 ORDER BY id DESC LIMIT 1")) {

            if (rs.next()) {
                currentShiftId = rs.getInt("id");
                currentShiftType = rs.getString("shift_type");
                currentShiftTimeEarnings = rs.getDouble("time_earnings");
                currentShiftOrdersEarnings = rs.getDouble("orders_earnings");
                currentShiftSessionsCount = rs.getInt("sessions_count");
                String startStr = rs.getString("start_time");
                try {
                    currentShiftStartTime = LocalDateTime.parse(startStr.replace(" ", "T"));
                } catch (Exception e) {
                    currentShiftStartTime = LocalDateTime.now();
                }
            } else {
                // فتح وردية جديدة في الداتابيز
                startNewShiftInDB("صباحي ☀️");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void startNewShiftInDB(String type) {
        currentShiftType = type;
        currentShiftStartTime = LocalDateTime.now();
        currentShiftTimeEarnings = 0.0;
        currentShiftOrdersEarnings = 0.0;
        currentShiftSessionsCount = 0;

        String sql = "INSERT INTO shifts (shift_type, start_time, is_closed) VALUES (?, ?, 0)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, currentShiftType);
            pstmt.setString(2, currentShiftStartTime.toString().replace("T", " "));
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                currentShiftId = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadShiftHistoryFromDB() {
        shiftHistory.clear();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM shifts WHERE is_closed = 1 ORDER BY id DESC")) {

            while (rs.next()) {
                LocalDateTime start = LocalDateTime.now();
                LocalDateTime end = LocalDateTime.now();
                try {
                    start = LocalDateTime.parse(rs.getString("start_time").replace(" ", "T"));
                    end = LocalDateTime.parse(rs.getString("end_time").replace(" ", "T"));
                } catch (Exception ignored) {
                }

                shiftHistory.add(new ShiftRecord(
                        rs.getInt("id"),
                        rs.getString("shift_type"),
                        start,
                        end,
                        rs.getDouble("time_earnings"),
                        rs.getDouble("orders_earnings"),
                        rs.getInt("sessions_count")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CARD_BORDER, 1),
                new EmptyBorder(15, 25, 15, 25)
        ));

        // Title & Admin Button
        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        titleBox.setOpaque(false);

        JButton adminBtn = createStyledButton("⚙️ لوحة الأدمن", ACCENT_CYAN);
        adminBtn.setForeground(Color.BLACK);
        adminBtn.addActionListener(e -> showAdminLoginDialog());

        JLabel titleLabel = new JLabel("🎮 نظام إدارة صاله البلايستيشن ");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(ACCENT_CYAN);

        titleBox.add(adminBtn);
        titleBox.add(titleLabel);

        header.add(titleBox, BorderLayout.EAST);

        // Stats Panel
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 0));
        statsPanel.setOpaque(false);

        activeRoomsLabel = new JLabel("الغرف النشطة: 0 / " + rooms.size());
        activeRoomsLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        activeRoomsLabel.setForeground(TEXT_WHITE);

        totalEarningsLabel = new JLabel("إجمالي الدخل اليومي: 0.00 ج.م");
        totalEarningsLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        totalEarningsLabel.setForeground(STATUS_FREE);

        statsPanel.add(activeRoomsLabel);
        statsPanel.add(totalEarningsLabel);

        header.add(statsPanel, BorderLayout.WEST);
        return header;
    }

    private JScrollPane createMainGridPanel() {
        gridPanel = new JPanel(new GridLayout(0, 3, 20, 20));
        gridPanel.setBackground(BG_DARK);
        gridPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        renderRoomCards();

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_DARK);
        return scrollPane;
    }

    private void renderRoomCards() {
        gridPanel.removeAll();
        for (Room room : rooms) {
            gridPanel.add(createRoomCard(room));
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel createRoomCard(Room room) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(room.isOccupied() ? ACCENT_BLUE : (room.isVip() ? ACCENT_GOLD : CARD_BORDER), 2, true),
                new EmptyBorder(15, 15, 15, 15)
        ));

        // Top Header inside card
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        JPanel titleBadgeBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        titleBadgeBox.setOpaque(false);

        if (room.isVip()) {
            JLabel vipBadge = new JLabel("👑 VIP");
            vipBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
            vipBadge.setOpaque(true);
            vipBadge.setBackground(ACCENT_GOLD);
            vipBadge.setForeground(Color.BLACK);
            vipBadge.setBorder(new EmptyBorder(3, 6, 3, 6));
            titleBadgeBox.add(vipBadge);
        }

        JLabel nameLabel = new JLabel(room.getName());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        nameLabel.setForeground(TEXT_WHITE);
        titleBadgeBox.add(nameLabel);

        JLabel statusLabel = new JLabel(room.isOccupied() ? " 🔴 مشغول " : " 🟢 متاح ");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(room.isOccupied() ? new Color(153, 27, 27) : new Color(6, 78, 59));
        statusLabel.setForeground(TEXT_WHITE);
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        topPanel.add(titleBadgeBox, BorderLayout.EAST);
        topPanel.add(statusLabel, BorderLayout.WEST);

        // Center section: Timer & Costs
        JPanel centerPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        centerPanel.setOpaque(false);

        JLabel timerLabel = new JLabel(room.getFormattedDuration(), SwingConstants.CENTER);
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 32));
        timerLabel.setForeground(room.isOccupied() ? ACCENT_CYAN : TEXT_MUTED);

        JLabel typeLabel = new JLabel(room.isOccupied()
                ? ("نوع اللعب: " + (room.isMultiplayer() ? "Multi (ملتي)" : "Single (سنجل)"))
                : ("سنجل: " + room.getSingleRate() + "ج | ملتي: " + room.getMultiRate() + "ج"), SwingConstants.CENTER);
        typeLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        typeLabel.setForeground(TEXT_MUTED);

        DecimalFormat df = new DecimalFormat("#0.00");
        double currentTotal = room.calculateTimeCost() + room.calculateOrdersCost();
        JLabel costLabel = new JLabel("الحساب الحالي: " + df.format(currentTotal) + " ج.م", SwingConstants.CENTER);
        costLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        costLabel.setForeground(room.isOccupied() ? STATUS_FREE : TEXT_MUTED);

        centerPanel.add(timerLabel);
        centerPanel.add(typeLabel);
        centerPanel.add(costLabel);

        // Bottom Action Buttons
        JPanel actionsPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        actionsPanel.setOpaque(false);

        JButton btnStart = createStyledButton("ابدأ", ACCENT_BLUE);
        JButton btnOrder = createStyledButton("+ طلبات", new Color(107, 114, 128));
        JButton btnEnd = createStyledButton("إغلاق", STATUS_BUSY);

        btnStart.setEnabled(!room.isOccupied());
        btnOrder.setEnabled(room.isOccupied());
        btnEnd.setEnabled(room.isOccupied());

        btnStart.addActionListener(e -> showStartDialog(room));
        btnOrder.addActionListener(e -> showAddOrderDialog(room));
        btnEnd.addActionListener(e -> checkoutRoom(room));

        actionsPanel.add(btnStart);
        actionsPanel.add(btnOrder);
        actionsPanel.add(btnEnd);

        card.add(topPanel, BorderLayout.NORTH);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(actionsPanel, BorderLayout.SOUTH);

        // Nقر مزدوج لعرض قائمة طلبات الغرفة
        java.awt.event.MouseAdapter doubleClickAdapter = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showRoomOrdersDialog(room);
                }
            }
        };

        card.addMouseListener(doubleClickAdapter);
        centerPanel.addMouseListener(doubleClickAdapter);
        topPanel.addMouseListener(doubleClickAdapter);

        return card;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 5, 8, 5));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void showStartDialog(Room room) {
        JDialog dialog = new JDialog(this, "بدء جلسة - " + room.getName(), true);
        dialog.setLayout(new GridLayout(3, 1, 10, 10));
        dialog.setSize(380, 220);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);

        JLabel prompt = new JLabel("اختر نظام اللعب:", SwingConstants.CENTER);
        prompt.setFont(new Font("SansSerif", Font.BOLD, 16));
        prompt.setForeground(TEXT_WHITE);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setOpaque(false);

        JButton singleBtn = createStyledButton("Single (" + room.getSingleRate() + "ج/س)", ACCENT_BLUE);
        JButton multiBtn = createStyledButton("Multi (" + room.getMultiRate() + "ج/س)", ACCENT_CYAN);
        multiBtn.setForeground(Color.BLACK);

        singleBtn.addActionListener(e -> {
            startSessionInDB(room, false);
            dialog.dispose();
            renderRoomCards();
            updateStats();
        });

        multiBtn.addActionListener(e -> {
            startSessionInDB(room, true);
            dialog.dispose();
            renderRoomCards();
            updateStats();
        });

        btnPanel.add(singleBtn);
        btnPanel.add(multiBtn);

        dialog.add(prompt);
        dialog.add(btnPanel);
        dialog.setVisible(true);
    }

    private void startSessionInDB(Room room, boolean isMulti) {
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO sessions (room_id, shift_id, play_type, start_time, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, room.getId());
            pstmt.setInt(2, currentShiftId);
            pstmt.setString(3, isMulti ? "MULTI" : "SINGLE");
            pstmt.setString(4, now.toString().replace("T", " "));
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int sessionId = rs.getInt(1);
                room.startSession(sessionId, isMulti, now);

                // تحديث حالة الغرفة في الداتابيز
                try (PreparedStatement uStmt = conn.prepareStatement("UPDATE rooms SET is_occupied = 1 WHERE id = ?")) {
                    uStmt.setInt(1, room.getId());
                    uStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "خطأ أثناء حفظ الجلسة في قاعدة البيانات: " + e.getMessage());
        }
    }

    private void showAddOrderDialog(Room room) {
        if (menuItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "قائمة المنيو فارغة حالياً. برجاء إضافتها من لوحة الأدمن.");
            return;
        }

        JDialog dialog = new JDialog(this, "➕ إضافة طلبات - " + room.getName(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(440, 240);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);
        ((JPanel) dialog.getContentPane()).setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel formPanel = new JPanel(new GridLayout(2, 2, 12, 15));
        formPanel.setOpaque(false);

        JComboBox<MenuItem> menuCombo = new JComboBox<>(menuItems.toArray(new MenuItem[0]));
        menuCombo.setFont(new Font("SansSerif", Font.BOLD, 14));
        menuCombo.setBackground(new Color(31, 41, 55));
        menuCombo.setForeground(TEXT_WHITE);

        JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        qtySpinner.setFont(new Font("SansSerif", Font.BOLD, 15));

        formPanel.add(createCustomLabel("المشروب / الطلب:"));
        formPanel.add(menuCombo);
        formPanel.add(createCustomLabel("الكمية المطلوبـة:"));
        formPanel.add(qtySpinner);

        JButton addBtn = createStyledButton("➕ إضافة للجلسة الحالية", STATUS_FREE);
        addBtn.setPreferredSize(new Dimension(0, 45));
        addBtn.addActionListener(e -> {
            MenuItem item = (MenuItem) menuCombo.getSelectedItem();
            if (item != null) {
                int qty = (Integer) qtySpinner.getValue();
                addOrderToDB(room, item, qty);
                dialog.dispose();
                renderRoomCards();
            }
        });

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(addBtn, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void addOrderToDB(Room room, MenuItem item, int qty) {
        double total = item.getPrice() * qty;
        String sql = "INSERT INTO session_orders (session_id, item_name, unit_price, quantity, total_price) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, room.getActiveSessionId());
            pstmt.setString(2, item.getName());
            pstmt.setDouble(3, item.getPrice());
            pstmt.setInt(4, qty);
            pstmt.setDouble(5, total);
            pstmt.executeUpdate();

            // إضافته للغرفة في الذاكرة
            room.addOrderInMemory(item.getName(), item.getPrice(), qty);

            // تحديث تكلفة الطلبات في جدول الجلسات
            try (PreparedStatement uStmt = conn.prepareStatement("UPDATE sessions SET orders_cost = ? WHERE id = ?")) {
                uStmt.setDouble(1, room.calculateOrdersCost());
                uStmt.setInt(2, room.getActiveSessionId());
                uStmt.executeUpdate();
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "خطأ في إضافة الطلب للداتابيز: " + e.getMessage());
        }
    }

    private void showRoomOrdersDialog(Room room) {
        if (!room.isOccupied()) {
            JOptionPane.showMessageDialog(this, "الغرفة غير مشغولة حالياً ولا يوجد لها طلبات.", "تنبيه", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "📦 قائمة طلبات ومشاريب - " + room.getName(), true);
        dialog.setSize(550, 420);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);
        dialog.setLayout(new BorderLayout(15, 15));

        String[] columns = {"اسم الطلب", "سعر الوحدة", "الكمية", "الإجمالي"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        DecimalFormat df = new DecimalFormat("#0.00");
        for (OrderItem item : room.getOrders()) {
            model.addRow(new Object[]{
                item.getName(),
                df.format(item.getUnitPrice()) + " ج.م",
                "x " + item.getQuantity(),
                df.format(item.getTotalPrice()) + " ج.م"
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 15, 0, 15));

        JLabel titleLbl = new JLabel("🍹 المشاريب والطلبات المضافة للجلسة النشطة:", SwingConstants.RIGHT);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLbl.setForeground(ACCENT_CYAN);
        headerPanel.add(titleLbl, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(10, 15, 15, 15));

        JLabel totalLbl = new JLabel("إجمالي حساب الطلبات: " + df.format(room.calculateOrdersCost()) + " ج.م");
        totalLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        totalLbl.setForeground(STATUS_FREE);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnPanel.setOpaque(false);

        JButton deleteBtn = createStyledButton("🗑️ حذف الطلب المحدد", STATUS_BUSY);
        deleteBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(dialog, "برجاء تحديد عنصر من القائمة لإلغائه.");
                return;
            }

            OrderItem itemToRemove = room.getOrders().get(selectedRow);
            deleteOrderFromDB(room, itemToRemove);

            room.getOrders().remove(selectedRow);
            model.removeRow(selectedRow);
            totalLbl.setText("إجمالي حساب الطلبات: " + df.format(room.calculateOrdersCost()) + " ج.م");
            renderRoomCards();
        });

        btnPanel.add(deleteBtn);
        bottomPanel.add(totalLbl, BorderLayout.EAST);
        bottomPanel.add(btnPanel, BorderLayout.WEST);

        dialog.add(headerPanel, BorderLayout.NORTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void deleteOrderFromDB(Room room, OrderItem item) {
        String sql = "DELETE FROM session_orders WHERE session_id = ? AND item_name = ? AND quantity = ? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, room.getActiveSessionId());
            pstmt.setString(2, item.getName());
            pstmt.setInt(3, item.getQuantity());
            pstmt.executeUpdate();

            // تحديث تكلفة الطلبات في جدول الجلسات
            try (PreparedStatement uStmt = conn.prepareStatement("UPDATE sessions SET orders_cost = ? WHERE id = ?")) {
                uStmt.setDouble(1, room.calculateOrdersCost() - item.getTotalPrice());
                uStmt.setInt(2, room.getActiveSessionId());
                uStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void checkoutRoom(Room room) {
        Receipt receipt = room.endSession();
        if (receipt == null) {
            return;
        }

        // تحديث إغلاق الجلسة في الـ SQLite Database
        closeSessionInDB(room, receipt);

        // إضافة الأرباح لإحصائيات اليوم والوردية الحالية
        totalDailyEarnings += receipt.getTotalCost();
        currentShiftTimeEarnings += receipt.getTimeCost();
        currentShiftOrdersEarnings += receipt.getOrdersCost();
        currentShiftSessionsCount++;

        // تحديث أرباح الوردية الحالية في الداتابيز
        updateShiftEarningsInDB();

        updateStats();

        // عرض فاتورة الفاتورة الأنيقة باللون الأبيض للطباعة
        showWhiteReceiptDialog(receipt);
    }

    private void closeSessionInDB(Room room, Receipt receipt) {
        String sql = "UPDATE sessions SET end_time = ?, duration_minutes = ?, time_cost = ?, orders_cost = ?, total_cost = ?, status = 'COMPLETED' WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, receipt.getEndTime().toString().replace("T", " "));
            pstmt.setLong(2, receipt.getDurationMinutes());
            pstmt.setDouble(3, receipt.getTimeCost());
            pstmt.setDouble(4, receipt.getOrdersCost());
            pstmt.setDouble(5, receipt.getTotalCost());
            pstmt.setInt(6, receipt.getSessionId());
            pstmt.executeUpdate();

            // جعل الغرفة متاحة في الداتابيز
            try (PreparedStatement uStmt = conn.prepareStatement("UPDATE rooms SET is_occupied = 0 WHERE id = ?")) {
                uStmt.setInt(1, room.getId());
                uStmt.executeUpdate();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "خطأ في إغلاق الجلسة بالداتابيز: " + e.getMessage());
        }
    }

    private void updateShiftEarningsInDB() {
        String sql = "UPDATE shifts SET time_earnings = ?, orders_earnings = ?, total_earnings = ?, sessions_count = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, currentShiftTimeEarnings);
            pstmt.setDouble(2, currentShiftOrdersEarnings);
            pstmt.setDouble(3, currentShiftTimeEarnings + currentShiftOrdersEarnings);
            pstmt.setInt(4, currentShiftSessionsCount);
            pstmt.setInt(5, currentShiftId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showAdminLoginDialog() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(new Font("SansSerif", Font.BOLD, 16));
        int option = JOptionPane.showConfirmDialog(
                this,
                new Object[]{"ادخل كلمة مرور الأدمن للوصول للإعدادات:", pf},
                "تسجيل دخول الأدمن",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            String password = new String(pf.getPassword());
            if (password.equals(adminPassword)) {
                showAdminPanel();
            } else {
                JOptionPane.showMessageDialog(this, "كلمة المرور غير صحيحة!", "خطأ", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAdminPanel() {
        JDialog adminDialog = new JDialog(this, "⚙️ لوحة تحكم الأدمن والورديات والأسعار (SQLite Manager)", true);
        adminDialog.setSize(920, 600);
        adminDialog.setLocationRelativeTo(this);
        adminDialog.getContentPane().setBackground(CARD_BG);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 14));
// إضافة التبويب الجديد لـ JTabbedPane
        tabbedPane.addTab("👥 إدارة وتقارير الموظفين", createAdminEmployeesPanel());
        // Tab 1: Rooms & Rates Management
        tabbedPane.addTab("🎮 إدارة الغرف والأسعار", createAdminRoomsPanel());

        // Tab 2: Menu / Drinks Management
        tabbedPane.addTab("☕ إدارة قائمة المشاريب والطلبات", createAdminMenuPanel());

        // Tab 3: Shift Management & Shift History
        tabbedPane.addTab("🌇 تقفيل وسجل الورديات", createAdminShiftPanel());

        adminDialog.add(tabbedPane);
        adminDialog.setVisible(true);
    }

    private JPanel createAdminEmployeesPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // جدول عرض الموظفين
        String[] columns = {"ID", "اسم الموظف", "رقم الهاتف", "الوظيفة", "كود الدخول (PIN)"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        List<Employee> employees = loadEmployeesFromDB();
        for (Employee emp : employees) {
            model.addRow(new Object[]{emp.getId(), emp.getName(), emp.getPhone(), emp.getRole(), "****"});
        }

        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));

        // شريط الأزرار (إضافة - تعديل - تقرير مفصل للطباعة)
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnBar.setOpaque(false);

        JButton addEmpBtn = createStyledButton("+ إضافة موظف جديد", ACCENT_BLUE);
        JButton printReportBtn = createStyledButton("🖨️ طباعة تقرير أداء الموظفين", ACCENT_CYAN);
        printReportBtn.setForeground(Color.BLACK);

        // إضافة موظف جديد
        addEmpBtn.addActionListener(e -> {
            JTextField nameFld = new JTextField();
            JTextField phoneFld = new JTextField();
            JTextField pinFld = new JTextField();

            Object[] message = {
                "اسم الموظف:", nameFld,
                "رقم الهاتف:", phoneFld,
                "كود الدخول السرّي (PIN):", pinFld
            };

            int option = JOptionPane.showConfirmDialog(this, message, "إضافة موظف جديد", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                saveEmployeeToDB(nameFld.getText().trim(), phoneFld.getText().trim(), "كاشير", pinFld.getText().trim());
                // إعادة تحميل لوحة الأدمن لتحديث القائمة
                showAdminPanel();
            }
        });

        // طباعة تقرير تفصيلي شامل لكل الموظفين والورديات
        printReportBtn.addActionListener(e -> printEmployeeDetailedReport(table));

        btnBar.add(addEmpBtn);
        btnBar.add(printReportBtn);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(btnBar, BorderLayout.SOUTH);
        return panel;
    }

// دالة تحميل الموظفين من الداتابيز
    private List<Employee> loadEmployeesFromDB() {
        List<Employee> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM employees ORDER BY id DESC")) {
            while (rs.next()) {
                list.add(new Employee(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("role"),
                        rs.getString("pin_code")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

// دالة حفظ موظف جديد في الداتابيز
    private void saveEmployeeToDB(String name, String phone, String role, String pin) {
        String sql = "INSERT INTO employees (name, phone, role, pin_code) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, phone);
            pstmt.setString(3, role);
            pstmt.setString(4, pin);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "تم إضافة الموظف بنجاح!");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "خطأ: كود الـ PIN يجب أن يكون فريداً وغير مكرر.");
        }
    }

    private void printEmployeeDetailedReport(JTable table) {
        try {
            boolean complete = table.print(
                    JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat("سجل وتقارير الموظفين - PlayStation Lounge"),
                    new java.text.MessageFormat("صفحة {0}")
            );
            if (complete) {
                JOptionPane.showMessageDialog(this, "تم إرسال التقرير إلى الطابعة بنجاح!");
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "خطأ في الطباعة: " + ex.getMessage());
        }
    }

    private JPanel createAdminRoomsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] columns = {"رقم الغرفة", "اسم الغرفة", "سعر السنجل (ج/س)", "سعر الملتي (ج/س)", "تصنيف VIP"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (Room r : rooms) {
            model.addRow(new Object[]{r.getId(), r.getName(), r.getSingleRate(), r.getMultiRate(), r.isVip() ? "نعم 👑" : "لا"});
        }

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnBar.setOpaque(false);

        JButton addBtn = createStyledButton("+ إضافة غرفة جديدة", ACCENT_BLUE);
        JButton editBtn = createStyledButton("✏️ تعديل الغرفة", ACCENT_CYAN);
        editBtn.setForeground(Color.BLACK);
        JButton deleteBtn = createStyledButton("🗑️ حذف الغرفة", STATUS_BUSY);

        addBtn.addActionListener(e -> {
            Room newRoom = new Room(0, "غرفة جديدة", 30.0, 45.0, false);
            showEditRoomDialog(newRoom, model, -1);
        });

        editBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(panel, "اختر غرفة لتعديل بياناتها.");
                return;
            }
            Room room = rooms.get(selectedRow);
            showEditRoomDialog(room, model, selectedRow);
        });

        deleteBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(panel, "اختر غرفة للحذف.");
                return;
            }
            Room roomToDelete = rooms.get(selectedRow);
            if (roomToDelete.isOccupied()) {
                JOptionPane.showMessageDialog(panel, "لا يمكن حذف غرفة مشغولة بجلسة نشطة!");
                return;
            }

            deleteRoomFromDB(roomToDelete.getId());
            rooms.remove(selectedRow);
            model.removeRow(selectedRow);
            renderRoomCards();
            updateStats();
        });

        btnBar.add(addBtn);
        btnBar.add(editBtn);
        btnBar.add(deleteBtn);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(btnBar, BorderLayout.SOUTH);
        return panel;
    }

    private void showEditRoomDialog(Room room, DefaultTableModel model, int row) {
        JDialog dialog = new JDialog(this, "تعديل بيانات الغرفة", true);
        dialog.setLayout(new GridLayout(6, 2, 10, 10));
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);
        ((JPanel) dialog.getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField nameFld = new JTextField(room.getName());
        JTextField singleFld = new JTextField(String.valueOf(room.getSingleRate()));
        JTextField multiFld = new JTextField(String.valueOf(room.getMultiRate()));
        JCheckBox vipChk = new JCheckBox("غرفة VIP 👑", room.isVip());
        vipChk.setFont(new Font("SansSerif", Font.BOLD, 14));
        vipChk.setForeground(ACCENT_GOLD);
        vipChk.setOpaque(false);

        JButton saveBtn = createStyledButton("حفظ التغيرات", STATUS_FREE);
        saveBtn.addActionListener(e -> {
            try {
                String name = nameFld.getText().trim();
                double single = Double.parseDouble(singleFld.getText().trim());
                double multi = Double.parseDouble(multiFld.getText().trim());
                boolean isVip = vipChk.isSelected();

                room.setName(name);
                room.setSingleRate(single);
                room.setMultiRate(multi);
                room.setVip(isVip);

                saveRoomToDB(room);
                loadRoomsFromDB();

                dialog.dispose();
                renderRoomCards();
                updateStats();
                JOptionPane.showMessageDialog(this, "تم حفظ بيانات الغرفة في الداتابيز بنجاح!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "تأكد من كتابة أسعار أرقام صحيحة.");
            }
        });

        dialog.add(createCustomLabel("اسم الغرفة:"));
        dialog.add(nameFld);
        dialog.add(createCustomLabel("سعر السنجل (ج/س):"));
        dialog.add(singleFld);
        dialog.add(createCustomLabel("سعر الملتي (ج/س):"));
        dialog.add(multiFld);
        dialog.add(createCustomLabel("تصنيف VIP:"));
        dialog.add(vipChk);
        dialog.add(new JLabel());
        dialog.add(saveBtn);

        dialog.setVisible(true);
    }

    private void saveRoomToDB(Room room) {
        if (room.getId() == 0) {
            String sql = "INSERT INTO rooms (name, single_rate, multi_rate, is_vip, is_occupied) VALUES (?, ?, ?, ?, 0)";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, room.getName());
                pstmt.setDouble(2, room.getSingleRate());
                pstmt.setDouble(3, room.getMultiRate());
                pstmt.setInt(4, room.isVip() ? 1 : 0);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            String sql = "UPDATE rooms SET name = ?, single_rate = ?, multi_rate = ?, is_vip = ? WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, room.getName());
                pstmt.setDouble(2, room.getSingleRate());
                pstmt.setDouble(3, room.getMultiRate());
                pstmt.setInt(4, room.isVip() ? 1 : 0);
                pstmt.setInt(5, room.getId());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteRoomFromDB(int roomId) {
        String sql = "DELETE FROM rooms WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private JPanel createAdminMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] columns = {"ID", "اسم المشروب/الطلب", "السعر (ج.م)"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (MenuItem item : menuItems) {
            model.addRow(new Object[]{item.getId(), item.getName(), item.getPrice()});
        }

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnBar.setOpaque(false);

        JButton addItemBtn = createStyledButton("+ إضافة مشروب جديد", ACCENT_BLUE);
        JButton editItemBtn = createStyledButton("✏️ تعديل السعر", ACCENT_CYAN);
        editItemBtn.setForeground(Color.BLACK);
        JButton deleteItemBtn = createStyledButton("🗑️ حذف من المنيو", STATUS_BUSY);

        addItemBtn.addActionListener(e -> showEditMenuItemDialog(null, model, -1));

        editItemBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(panel, "اختر عنصر من المنيو للتعديل.");
                return;
            }
            MenuItem item = menuItems.get(selectedRow);
            showEditMenuItemDialog(item, model, selectedRow);
        });

        deleteItemBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(panel, "اختر عنصر للحذف.");
                return;
            }
            MenuItem item = menuItems.get(selectedRow);
            deleteMenuItemFromDB(item.getId());
            menuItems.remove(selectedRow);
            model.removeRow(selectedRow);
        });

        btnBar.add(addItemBtn);
        btnBar.add(editItemBtn);
        btnBar.add(deleteItemBtn);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(btnBar, BorderLayout.SOUTH);
        return panel;
    }

    private void showEditMenuItemDialog(MenuItem item, DefaultTableModel model, int row) {
        JDialog dialog = new JDialog(this, item == null ? "إضافة مشروب جديد" : "تعديل مشروب", true);
        dialog.setLayout(new GridLayout(3, 2, 10, 10));
        dialog.setSize(380, 200);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);
        ((JPanel) dialog.getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField nameFld = new JTextField(item != null ? item.getName() : "");
        JTextField priceFld = new JTextField(item != null ? String.valueOf(item.getPrice()) : "");

        JButton saveBtn = createStyledButton("حفظ", STATUS_FREE);
        saveBtn.addActionListener(e -> {
            try {
                String name = nameFld.getText().trim();
                double price = Double.parseDouble(priceFld.getText().trim());

                if (name.isEmpty()) {
                    return;
                }

                if (item == null) {
                    saveMenuItemToDB(name, price);
                } else {
                    updateMenuItemInDB(item.getId(), name, price);
                }

                loadMenuFromDB();
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "أدخل سعر صحيح.");
            }
        });

        dialog.add(createCustomLabel("اسم المشروب:"));
        dialog.add(nameFld);
        dialog.add(createCustomLabel("السعر (ج.م):"));
        dialog.add(priceFld);
        dialog.add(new JLabel());
        dialog.add(saveBtn);

        dialog.setVisible(true);
    }

    private void saveMenuItemToDB(String name, double price) {
        String sql = "INSERT INTO menu_items (name, price) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setDouble(2, price);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateMenuItemInDB(int id, String name, double price) {
        String sql = "UPDATE menu_items SET name = ?, price = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setDouble(2, price);
            pstmt.setInt(3, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteMenuItemFromDB(int id) {
        String sql = "DELETE FROM menu_items WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private JPanel createAdminShiftPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        DecimalFormat df = new DecimalFormat("#0.00");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd | hh:mm a");

        // Top Panel: Current Active Shift Info Card
        JPanel currentShiftCard = new JPanel(new BorderLayout(15, 15));
        currentShiftCard.setBackground(new Color(23, 32, 51));
        currentShiftCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_BLUE, 2, true),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JPanel shiftTitleBox = new JPanel(new GridLayout(2, 1, 5, 5));
        shiftTitleBox.setOpaque(false);

        JLabel shiftTitleLbl = new JLabel("الوردية الحالية (#" + currentShiftId + "): " + currentShiftType);
        shiftTitleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        shiftTitleLbl.setForeground(ACCENT_CYAN);

        JLabel shiftStartLbl = new JLabel("وقت بدء الوردية: " + currentShiftStartTime.format(timeFmt));
        shiftStartLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        shiftStartLbl.setForeground(TEXT_MUTED);

        shiftTitleBox.add(shiftTitleLbl);
        shiftTitleBox.add(shiftStartLbl);

        // Stats inside current shift
        double shiftTotal = currentShiftTimeEarnings + currentShiftOrdersEarnings;
        JPanel shiftStatsBox = new JPanel(new GridLayout(2, 2, 10, 5));
        shiftStatsBox.setOpaque(false);

        JLabel sessionsCountLbl = new JLabel("الجلسات المغلقة: " + currentShiftSessionsCount);
        sessionsCountLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        sessionsCountLbl.setForeground(TEXT_WHITE);

        JLabel timeRevLbl = new JLabel("دخل وقت اللعب: " + df.format(currentShiftTimeEarnings) + " ج.م");
        timeRevLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        timeRevLbl.setForeground(TEXT_WHITE);

        JLabel ordersRevLbl = new JLabel("دخل المشاريب: " + df.format(currentShiftOrdersEarnings) + " ج.م");
        ordersRevLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        ordersRevLbl.setForeground(TEXT_WHITE);

        JLabel totalRevLbl = new JLabel("إجمالي دخل الوردية: " + df.format(shiftTotal) + " ج.م");
        totalRevLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        totalRevLbl.setForeground(STATUS_FREE);

        shiftStatsBox.add(sessionsCountLbl);
        shiftStatsBox.add(timeRevLbl);
        shiftStatsBox.add(ordersRevLbl);
        shiftStatsBox.add(totalRevLbl);

        // Buttons for shift action
        JPanel actionBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actionBox.setOpaque(false);

        JButton toggleTypeBtn = createStyledButton("☀️/🌙 تبديل نوع الوردية", ACCENT_BLUE);
        JButton closeShiftBtn = createStyledButton("🔒 تقفيل الوردية الحالية وحفظ التقرير", STATUS_BUSY);

        toggleTypeBtn.addActionListener(e -> {
            if (currentShiftType.contains("صباحي")) {
                currentShiftType = "مسائي 🌙";
            } else {
                currentShiftType = "صباحي ☀️";
            }
            shiftTitleLbl.setText("الوردية الحالية (#" + currentShiftId + "): " + currentShiftType);

            // تحديث نوع الوردية في الـ SQLite
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("UPDATE shifts SET shift_type = ? WHERE id = ?")) {
                pstmt.setString(1, currentShiftType);
                pstmt.setInt(2, currentShiftId);
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        closeShiftBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel,
                    "هل أنت تأكد من تقفيل الوردية الحالية (" + currentShiftType + ")؟\nسيتم حفظ إجمالي دخل (" + df.format(shiftTotal) + " ج.م) في الداتابيز وبدء وردية جديدة.",
                    "تأكيد تقفيل الوردية", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                closeShiftInDB();
                String nextType = currentShiftType.contains("صباحي") ? "مسائي 🌙" : "صباحي ☀️";
                startNewShiftInDB(nextType);
                loadShiftHistoryFromDB();

                JOptionPane.showMessageDialog(panel, "تم تقفيل الوردية وتخزين التقرير في الداتابيز بنجاح! تم فتح الوردية الجديدة (" + nextType + ").");

                Window w = SwingUtilities.getWindowAncestor(panel);
                if (w != null) {
                    w.dispose();
                }
                showAdminPanel();
            }
        });

        actionBox.add(toggleTypeBtn);
        actionBox.add(closeShiftBtn);

        currentShiftCard.add(shiftTitleBox, BorderLayout.NORTH);
        currentShiftCard.add(shiftStatsBox, BorderLayout.CENTER);
        currentShiftCard.add(actionBox, BorderLayout.SOUTH);

        // History Table
        String[] columns = {"#", "نوع الوردية", "وقت البداية", "وقت الإغلاق", "الجلسات", "دخل اللعب", "دخل الطلبات", "الإجمالي النهائي"};
        DefaultTableModel historyModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (ShiftRecord s : shiftHistory) {
            historyModel.addRow(new Object[]{
                "#" + s.getShiftId(),
                s.getShiftType(),
                s.getStartTime().format(timeFmt),
                s.getEndTime().format(timeFmt),
                s.getCompletedSessionsCount(),
                df.format(s.getTimeEarnings()) + " ج.م",
                df.format(s.getOrderEarnings()) + " ج.م",
                df.format(s.getTotalEarnings()) + " ج.م"
            });
        }

        JTable historyTable = new JTable(historyModel);
        historyTable.setRowHeight(30);
        historyTable.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel historyHeader = new JPanel(new BorderLayout());
        historyHeader.setOpaque(false);
        JLabel historyLbl = new JLabel(" سجل الورديات المغلقة السابقة :");
        historyLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        historyLbl.setForeground(ACCENT_GOLD);
        historyHeader.add(historyLbl, BorderLayout.EAST);

        panel.add(currentShiftCard, BorderLayout.NORTH);

        JPanel centerHistoryPanel = new JPanel(new BorderLayout(5, 5));
        centerHistoryPanel.setOpaque(false);
        centerHistoryPanel.add(historyHeader, BorderLayout.NORTH);
        centerHistoryPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        panel.add(centerHistoryPanel, BorderLayout.CENTER);
        return panel;
    }

    private void closeShiftInDB() {
        LocalDateTime now = LocalDateTime.now();
        String sql = "UPDATE shifts SET end_time = ?, time_earnings = ?, orders_earnings = ?, total_earnings = ?, sessions_count = ?, is_closed = 1 WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, now.toString().replace("T", " "));
            pstmt.setDouble(2, currentShiftTimeEarnings);
            pstmt.setDouble(3, currentShiftOrdersEarnings);
            pstmt.setDouble(4, currentShiftTimeEarnings + currentShiftOrdersEarnings);
            pstmt.setInt(5, currentShiftSessionsCount);
            pstmt.setInt(6, currentShiftId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showWhiteReceiptDialog(Receipt receipt) {
        JDialog receiptDialog = new JDialog(this, "فاتورة الحساب - " + receipt.getRoomName(), true);
        receiptDialog.setSize(500, 670);
        receiptDialog.setLocationRelativeTo(this);
        receiptDialog.getContentPane().setBackground(new Color(243, 244, 246));
        receiptDialog.setLayout(new BorderLayout(0, 0));

        JPanel paperCard = new JPanel(new BorderLayout(15, 15));
        paperCard.setBackground(Color.WHITE);
        paperCard.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(20, 20, 20, 20),
                new LineBorder(new Color(229, 231, 235), 1)
        ));

        // 1. Header
        JPanel headerPanel = new JPanel(new GridLayout(3, 1, 3, 3));
        headerPanel.setBackground(Color.WHITE);

        JLabel logoLabel = new JLabel("🎮 PLAYSTATION LOUNGE", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        logoLabel.setForeground(new Color(17, 24, 39));

        JLabel subLogoLabel = new JLabel("إيصال سداد جلسة ومشاريب (سجل رقم #" + receipt.getSessionId() + ")", SwingConstants.CENTER);
        subLogoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subLogoLabel.setForeground(new Color(107, 114, 128));

        JLabel divider = new JLabel("----------------------------------------------------------------", SwingConstants.CENTER);
        divider.setForeground(new Color(209, 213, 219));

        headerPanel.add(logoLabel);
        headerPanel.add(subLogoLabel);
        headerPanel.add(divider);

        // 2. Details
        JPanel detailsPanel = new JPanel(new GridLayout(5, 2, 8, 6));
        detailsPanel.setBackground(new Color(249, 250, 251));
        detailsPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(243, 244, 246), 1),
                new EmptyBorder(12, 15, 12, 15)
        ));

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");
        DecimalFormat df = new DecimalFormat("#0.00");

        addReceiptDetailRow(detailsPanel, "اسم الغرفـة:", receipt.getRoomName());
        addReceiptDetailRow(detailsPanel, "الوردية الحالية:", currentShiftType);
        addReceiptDetailRow(detailsPanel, "وقت البداية:", receipt.getStartTime().format(timeFmt));
        addReceiptDetailRow(detailsPanel, "وقت النهاية:", receipt.getEndTime().format(timeFmt));
        addReceiptDetailRow(detailsPanel, "مدة الجلسة:", receipt.getDurationMinutes() + " دقيقة و " + receipt.getDurationSeconds() + " ثانية");

        // 3. Table
        String[] cols = {"البيان / المادة", "الكمية", "السعر", "الإجمالي"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        model.addRow(new Object[]{
            "حساب وقت اللعب",
            "1",
            df.format(receipt.getTimeCost()) + " ج.م",
            df.format(receipt.getTimeCost()) + " ج.م"
        });

        for (OrderItem order : receipt.getOrders()) {
            model.addRow(new Object[]{
                order.getName(),
                "x" + order.getQuantity(),
                df.format(order.getUnitPrice()) + " ج.م",
                df.format(order.getTotalPrice()) + " ج.م"
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setBackground(Color.WHITE);
        table.setForeground(new Color(31, 41, 55));
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setBackground(new Color(243, 244, 246));
        table.getTableHeader().setForeground(new Color(17, 24, 39));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        // 4. Financial Total Summary Card
        JPanel totalBox = new JPanel(new BorderLayout(10, 5));
        totalBox.setBackground(new Color(16, 185, 129));
        totalBox.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel totalTxtLbl = new JLabel("المبلغ الإجمالي المطلـوب:");
        totalTxtLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        totalTxtLbl.setForeground(Color.WHITE);

        JLabel totalValLbl = new JLabel(df.format(receipt.getTotalCost()) + " ج.م");
        totalValLbl.setFont(new Font("SansSerif", Font.BOLD, 22));
        totalValLbl.setForeground(Color.WHITE);

        totalBox.add(totalTxtLbl, BorderLayout.EAST);
        totalBox.add(totalValLbl, BorderLayout.WEST);

        JPanel bodyPanel = new JPanel(new BorderLayout(10, 12));
        bodyPanel.setBackground(Color.WHITE);
        bodyPanel.add(detailsPanel, BorderLayout.NORTH);
        bodyPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        bodyPanel.add(totalBox, BorderLayout.SOUTH);

        paperCard.add(headerPanel, BorderLayout.NORTH);
        paperCard.add(bodyPanel, BorderLayout.CENTER);

        // Footer Action Buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 12));
        actionPanel.setBackground(new Color(243, 244, 246));

        JButton printBtn = createStyledButton("🖨️ طباعة الفاتورة", ACCENT_BLUE);
        JButton closeBtn = createStyledButton("✅ تم تحصيل المبلغ (إغلاق)", STATUS_FREE);

        printBtn.setPreferredSize(new Dimension(170, 42));
        closeBtn.setPreferredSize(new Dimension(210, 42));

        printBtn.addActionListener(e -> {
            try {
                table.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(receiptDialog, "خطأ أثناء محاولة الطباعة.");
            }
        });

        closeBtn.addActionListener(e -> {
            receiptDialog.dispose();
            renderRoomCards();
        });

        actionPanel.add(printBtn);
        actionPanel.add(closeBtn);

        receiptDialog.add(paperCard, BorderLayout.CENTER);
        receiptDialog.add(actionPanel, BorderLayout.SOUTH);
        receiptDialog.setVisible(true);
    }

    private void addReceiptDetailRow(JPanel panel, String label, String value) {
        JLabel lblName = new JLabel(label, SwingConstants.RIGHT);
        lblName.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblName.setForeground(new Color(75, 85, 99));

        JLabel lblVal = new JLabel(value, SwingConstants.LEFT);
        lblVal.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblVal.setForeground(new Color(17, 24, 39));

        panel.add(lblName);
        panel.add(lblVal);
    }

    private JLabel createCustomLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.RIGHT);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setForeground(TEXT_WHITE);
        return lbl;
    }

    private void updateAllRoomCards() {
        renderRoomCards();
    }

    private void updateStats() {
        long activeCount = rooms.stream().filter(Room::isOccupied).count();
        activeRoomsLabel.setText("الغرف النشطة: " + activeCount + " / " + rooms.size());
        DecimalFormat df = new DecimalFormat("#0.00");
        totalEarningsLabel.setText("إجمالي الدخل اليومي: " + df.format(totalDailyEarnings) + " ج.م");
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel();
        footer.setBackground(BG_DARK);
        JLabel lbl = new JLabel("© 2026 CYBER SYSTEMS • جميع الحقوق محفوظة | Technology • Solutions • Security.", SwingConstants.CENTER);
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        footer.add(lbl);
        return footer;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CyberSystemsSplashScreen splash = new CyberSystemsSplashScreen();
            splash.showSplashAndLaunch(() -> {
                PLAYSTION gui = new PLAYSTION();
                gui.setVisible(true);
            });
        });
    }

    public static class CyberSystemsSplashScreen extends JWindow {

        private final JProgressBar progressBar;
        private final JLabel statusLabel;
        private int progressValue = 0;

        public CyberSystemsSplashScreen() {
            setSize(620, 380);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            JPanel contentPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    GradientPaint bgGradient = new GradientPaint(0, 0, new Color(5, 8, 18), 0, getHeight(), new Color(11, 15, 25));
                    g2d.setPaint(bgGradient);
                    g2d.fillRect(0, 0, getWidth(), getHeight());

                    g2d.setColor(new Color(0, 240, 255, 100));
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawRect(1, 1, getWidth() - 2, getHeight() - 2);

                    int centerX = getWidth() / 2;
                    int centerY = 120;

                    g2d.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    GradientPaint ringGradient = new GradientPaint(centerX - 50, centerY - 50, ACCENT_CYAN, centerX + 50, centerY + 50, ACCENT_BLUE);
                    g2d.setPaint(ringGradient);
                    g2d.drawArc(centerX - 55, centerY - 55, 110, 110, 30, 310);

                    g2d.setFont(new Font("SansSerif", Font.BOLD, 64));
                    FontMetrics fm = g2d.getFontMetrics();
                    String sText = "S";
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(sText, centerX - (fm.stringWidth(sText) / 2), centerY + (fm.getAscent() / 3));

                    g2d.setFont(new Font("Arial Black", Font.BOLD, 28));
                    g2d.setColor(Color.WHITE);
                    String title = "CYBER SYSTEMS";
                    FontMetrics tfm = g2d.getFontMetrics();
                    g2d.drawString(title, centerX - (tfm.stringWidth(title) / 2), centerY + 90);

                    g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                    g2d.setColor(ACCENT_CYAN);
                    String sub1 = "TECHNOLOGY | SOLUTIONS | SECURITY";
                    FontMetrics sfm1 = g2d.getFontMetrics();
                    g2d.drawString(sub1, centerX - (sfm1.stringWidth(sub1) / 2), centerY + 115);

                    g2d.dispose();
                }
            };

            JPanel bottomPanel = new JPanel(new BorderLayout(5, 8));
            bottomPanel.setOpaque(false);
            bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 25, 30));

            statusLabel = new JLabel("جاري تحميل النظام والداتا...", SwingConstants.RIGHT);
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            statusLabel.setForeground(ACCENT_CYAN);

            progressBar = new JProgressBar(0, 100);
            progressBar.setPreferredSize(new Dimension(0, 8));
            progressBar.setBackground(new Color(20, 30, 48));
            progressBar.setForeground(ACCENT_CYAN);
            progressBar.setBorder(null);

            bottomPanel.add(statusLabel, BorderLayout.NORTH);
            bottomPanel.add(progressBar, BorderLayout.SOUTH);

            contentPanel.add(bottomPanel, BorderLayout.SOUTH);
            add(contentPanel);
        }

        public void showSplashAndLaunch(Runnable onComplete) {
            setVisible(true);

            Timer timer = new Timer(30, null);
            timer.addActionListener(e -> {
                progressValue += 2;
                progressBar.setValue(progressValue);

                if (progressValue == 20) {
                    statusLabel.setText("فحص وتجهيز جداول البيانات...");
                } else if (progressValue == 50) {
                    statusLabel.setText("تحميل الغرف والمنيو والورديات النشطة...");
                } else if (progressValue == 80) {
                    statusLabel.setText("تأكيد ربط البيانات التلقائي...");
                } else if (progressValue == 98) {
                    statusLabel.setText("تم الاتصال بنجاح! جاري فتح الواجهة...");
                }

                if (progressValue >= 100) {
                    timer.stop();
                    dispose();
                    onComplete.run();
                }
            });
            timer.start();
        }
    }

    public static class ShiftRecord {

        private int shiftId;
        private String shiftType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double timeEarnings;
        private double orderEarnings;
        private int completedSessionsCount;

        public ShiftRecord(int shiftId, String shiftType, LocalDateTime startTime, LocalDateTime endTime, double timeEarnings, double orderEarnings, int completedSessionsCount) {
            this.shiftId = shiftId;
            this.shiftType = shiftType;
            this.startTime = startTime;
            this.endTime = endTime;
            this.timeEarnings = timeEarnings;
            this.orderEarnings = orderEarnings;
            this.completedSessionsCount = completedSessionsCount;
        }

        public int getShiftId() {
            return shiftId;
        }

        public String getShiftType() {
            return shiftType;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public double getTimeEarnings() {
            return timeEarnings;
        }

        public double getOrderEarnings() {
            return orderEarnings;
        }

        public double getTotalEarnings() {
            return timeEarnings + orderEarnings;
        }

        public int getCompletedSessionsCount() {
            return completedSessionsCount;
        }
    }

    public static class Room {

        private int id;
        private String name;
        private double singleRate;
        private double multiRate;
        private boolean isVip;
        private boolean isOccupied;
        private boolean isMultiplayer;
        private int activeSessionId = -1;
        private LocalDateTime startTime;
        private List<OrderItem> orders = new ArrayList<>();

        public Room(int id, String name, double singleRate, double multiRate, boolean isVip) {
            this.id = id;
            this.name = name;
            this.singleRate = singleRate;
            this.multiRate = multiRate;
            this.isVip = isVip;
            this.isOccupied = false;
        }

        public void startSession(int sessionId, boolean isMulti, LocalDateTime start) {
            this.activeSessionId = sessionId;
            this.isOccupied = true;
            this.isMultiplayer = isMulti;
            this.startTime = start;
            this.orders.clear();
        }

        public void restoreActiveSession(int sessionId, boolean isMulti, LocalDateTime start) {
            this.activeSessionId = sessionId;
            this.isOccupied = true;
            this.isMultiplayer = isMulti;
            this.startTime = start;
        }

        public void addOrderInMemory(String name, double price, int qty) {
            orders.add(new OrderItem(name, price, qty));
        }

        public double calculateTimeCost() {
            if (!isOccupied || startTime == null) {
                return 0.0;
            }
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            double hours = duration.toSeconds() / 3600.0;
            double rate = isMultiplayer ? multiRate : singleRate;
            return hours * rate;
        }

        public double calculateOrdersCost() {
            return orders.stream().mapToDouble(OrderItem::getTotalPrice).sum();
        }

        public Receipt endSession() {
            if (!isOccupied) {
                return null;
            }
            LocalDateTime endTime = LocalDateTime.now();
            Receipt receipt = new Receipt(activeSessionId, name, startTime, endTime, calculateTimeCost(), calculateOrdersCost(), new ArrayList<>(orders));
            this.isOccupied = false;
            this.activeSessionId = -1;
            return receipt;
        }

        public String getFormattedDuration() {
            if (!isOccupied || startTime == null) {
                return "00:00:00";
            }
            Duration d = Duration.between(startTime, LocalDateTime.now());
            long HH = d.toHours();
            long MM = d.toMinutesPart();
            long SS = d.toSecondsPart();
            return String.format("%02d:%02d:%02d", HH, MM, SS);
        }

        public List<OrderItem> getOrders() {
            return orders;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isOccupied() {
            return isOccupied;
        }

        public boolean isMultiplayer() {
            return isMultiplayer;
        }

        public int getActiveSessionId() {
            return activeSessionId;
        }

        public double getSingleRate() {
            return singleRate;
        }

        public void setSingleRate(double singleRate) {
            this.singleRate = singleRate;
        }

        public double getMultiRate() {
            return multiRate;
        }

        public void setMultiRate(double multiRate) {
            this.multiRate = multiRate;
        }

        public boolean isVip() {
            return isVip;
        }

        public void setVip(boolean vip) {
            isVip = vip;
        }
    }

    public static class MenuItem {

        private int id;
        private String name;
        private double price;

        public MenuItem(int id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        @Override
        public String toString() {
            return name + " (" + price + " ج.م)";
        }
    }

    public static class OrderItem {

        private String name;
        private double unitPrice;
        private int quantity;

        public OrderItem(String name, double unitPrice, int quantity) {
            this.name = name;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getTotalPrice() {
            return unitPrice * quantity;
        }
    }

    public static class Receipt {

        private int sessionId;
        private String roomName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double timeCost;
        private double ordersCost;
        private List<OrderItem> orders;

        public Receipt(int sessionId, String roomName, LocalDateTime startTime, LocalDateTime endTime, double timeCost, double ordersCost, List<OrderItem> orders) {
            this.sessionId = sessionId;
            this.roomName = roomName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.timeCost = timeCost;
            this.ordersCost = ordersCost;
            this.orders = orders;
        }

        public int getSessionId() {
            return sessionId;
        }

        public String getRoomName() {
            return roomName;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public double getTimeCost() {
            return timeCost;
        }

        public double getOrdersCost() {
            return ordersCost;
        }

        public double getTotalCost() {
            return timeCost + ordersCost;
        }

        public List<OrderItem> getOrders() {
            return orders;
        }

        public long getDurationMinutes() {
            return Duration.between(startTime, endTime).toMinutes();
        }

        public long getDurationSeconds() {
            return Duration.between(startTime, endTime).toSecondsPart();
        }
    }
}
