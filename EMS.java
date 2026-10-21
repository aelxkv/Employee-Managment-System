import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.List;

/** Employee Management System - whole app in one file (Swing + JDBC). */
public class EMS {
    // ---- config: edit these to match your MySQL/MariaDB ----
    static final String URL = "jdbc:mysql://localhost:3306/ems_db", USER = "root", PASS = "root@123";

    // ---- theme ----
    static final Color NAVY = new Color(31, 56, 100), NAVY2 = new Color(46, 83, 149), BLUE = new Color(64, 133, 245),
            GREEN = new Color(39, 152, 92), RED = new Color(211, 68, 68), AMBER = new Color(224, 158, 42),
            BG = new Color(244, 246, 250), LINE = new Color(224, 228, 235), MUTED = new Color(108, 117, 125), DARK = new Color(33, 37, 41);
    static final Font F = new Font("Segoe UI", 0, 14), FB = new Font("Segoe UI", 1, 14), FT = new Font("Segoe UI", 1, 24);

    // ---- session / shell state ----
    static JFrame frame; static String role, user; static Integer empId;
    static CardLayout cl; static JPanel content, side;
    static Map<String, JButton> nav; static Map<String, Runnable> ref;
    record Item(int id, String name) { public String toString() { return name; } }

    // ================= DB helpers =================
    static List<Object[]> q(String sql, Object... p) {
        List<Object[]> r = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL, USER, PASS); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) s.setObject(i + 1, p[i]);
            try (ResultSet rs = s.executeQuery()) {
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) { Object[] o = new Object[n]; for (int i = 0; i < n; i++) o[i] = rs.getObject(i + 1); r.add(o); }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return r;
    }
    static boolean u(String sql, Object... p) {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) s.setObject(i + 1, p[i]);
            return s.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    static int n(String sql, Object... p) { List<Object[]> r = q(sql, p); return r.isEmpty() ? 0 : ((Number) r.get(0)[0]).intValue(); }
    static String s(Object o) { return o == null ? "" : o.toString(); }
    static boolean adm() { return role.equals("Admin") || role.equals("HR"); }
    static String sha(String t) {
        try { StringBuilder b = new StringBuilder(); for (byte x : MessageDigest.getInstance("SHA-256").digest(t.getBytes("UTF-8"))) b.append(String.format("%02x", x)); return b.toString(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static Item[] items(String sql) { return q(sql).stream().map(o -> new Item(((Number) o[0]).intValue(), s(o[1]))).toArray(Item[]::new); }
    static Item[] people() { return items("SELECT emp_id,CONCAT(first_name,' ',last_name,' (#',emp_id,')') FROM employee ORDER BY first_name"); }

    // ================= UI helpers =================
    static void aa(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
    static JLabel lbl(String t, Font f, Color c) { JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c); return l; }
    static Window win() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(); }
    static void warn(String m) { JOptionPane.showMessageDialog(win(), m, "Notice", JOptionPane.WARNING_MESSAGE); }
    static void info(String m) { JOptionPane.showMessageDialog(win(), m, "Done", JOptionPane.INFORMATION_MESSAGE); }
    static void close(Component c) { SwingUtilities.getWindowAncestor(c).dispose(); }
    static void clr(JTextComponent... c) { for (JTextComponent x : c) x.setText(""); }

    static JButton btn(String t, Color c, Runnable a) {
        JButton b = new JButton(t) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
                g2.setColor(!isEnabled() ? new Color(190, 195, 205) : getModel().isPressed() ? c.darker().darker() : getModel().isRollover() ? c.darker() : c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); g2.dispose(); super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false); b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(false);
        b.setForeground(Color.WHITE); b.setFont(FB); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.setBorder(new EmptyBorder(10, 20, 10, 20));
        b.addActionListener(e -> a.run());
        return b;
    }
    static JPanel card(LayoutManager l) {
        JPanel p = new JPanel(l) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
                g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(LINE); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16); g2.dispose();
            }
        };
        p.setOpaque(false); p.setBorder(new EmptyBorder(12, 12, 12, 12)); return p;
    }
    static <T extends JTextComponent> T fld(T t) {
        t.setFont(F); t.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(7, 10, 7, 10))); return t;
    }

    /** Vertical form: label + input rows, plus a button row. */
    static class Form extends JPanel {
        Form() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); }
        <T extends JComponent> T row(String label, T c) {
            JLabel l = lbl(label, FB, DARK); l.setAlignmentX(0); l.setBorder(new EmptyBorder(8, 0, 4, 0));
            c.setFont(F); c.setAlignmentX(0); c.setPreferredSize(new Dimension(320, 38)); c.setMaximumSize(new Dimension(460, 38));
            add(l); add(c); return c;
        }
        JTextField tf(String l) { return row(l, fld(new JTextField())); }
        JPasswordField pf(String l) { return row(l, fld(new JPasswordField())); }
        void note(String t) { JLabel l = lbl("<html><body style='width:300px'>" + t + "</body></html>", new Font("Segoe UI", 0, 12), MUTED); l.setAlignmentX(0); add(l); }
        void btns(JButton... bs) {
            JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); r.setOpaque(false); r.setAlignmentX(0); r.setMaximumSize(new Dimension(460, 44));
            for (JButton b : bs) r.add(b);
            add(Box.createVerticalStrut(14)); add(r);
        }
    }
    static JDialog dlg(String title, Form f, int w, int h) {
        JDialog d = new JDialog(frame, title, true);
        f.setBorder(new EmptyBorder(20, 24, 20, 24)); f.setOpaque(true); f.setBackground(Color.WHITE);
        d.add(new JScrollPane(f)); d.setSize(w, h); d.setLocationRelativeTo(frame); return d;
    }
    static JTable tbl(String... cols) {
        JTable t = new JTable(new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; } });
        t.setRowHeight(32); t.setFont(F); t.getTableHeader().setFont(FB); t.setShowVerticalLines(false); t.setGridColor(LINE);
        t.setSelectionBackground(new Color(224, 234, 250)); t.setSelectionForeground(DARK); return t;
    }
    static void fill(JTable t, List<Object[]> rows) { DefaultTableModel m = (DefaultTableModel) t.getModel(); m.setRowCount(0); for (Object[] r : rows) m.addRow(r); }
    static JPanel tcard(JTable t) { JPanel c = card(new BorderLayout()); JScrollPane sp = new JScrollPane(t); sp.setBorder(null); c.add(sp); return c; }
    static int sel(JTable t) {
        int r = t.getSelectedRow();
        if (r < 0) { warn("Select a row first."); return -1; }
        return ((Number) t.getValueAt(r, 0)).intValue();
    }
    /** Form card on top, table card below. */
    static JPanel stack(Form f, JTable t) {
        JPanel c = card(new BorderLayout()); c.add(f);
        JPanel p = new JPanel(new BorderLayout(0, 20)); p.setOpaque(false); p.add(c, BorderLayout.NORTH); p.add(tcard(t)); return p;
    }
    static JPanel page(String title, JComponent body, JComponent hdr) {
        JPanel p = new JPanel(new BorderLayout(0, 20)); p.setBackground(BG); p.setBorder(new EmptyBorder(28, 28, 28, 28));
        JPanel h = new JPanel(new BorderLayout()); h.setOpaque(false); h.add(lbl(title, FT, DARK), BorderLayout.WEST);
        if (hdr != null) h.add(hdr, BorderLayout.EAST);
        p.add(h, BorderLayout.NORTH); p.add(body); return p;
    }

    // ================= shell / navigation =================
    static JButton navBtn(String label, Runnable a) {
        JButton b = new JButton(label) {
            protected void paintComponent(Graphics g) {
                boolean on = Boolean.TRUE.equals(getClientProperty("on"));
                if (on || getModel().isRollover()) {
                    Graphics2D g2 = (Graphics2D) g.create(); aa(g2); g2.setColor(on ? BLUE : NAVY2);
                    g2.fillRoundRect(10, 3, getWidth() - 20, getHeight() - 6, 10, 10); g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false); b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(false);
        b.setForeground(Color.WHITE); b.setFont(F); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(12, 24, 12, 12)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.setAlignmentX(0);
        b.setMaximumSize(new Dimension(240, 46)); b.setPreferredSize(new Dimension(240, 46)); b.addActionListener(e -> a.run());
        return b;
    }
    /** Registers a page: sidebar button + card + refresh action. */
    static void add(String key, String label, JPanel pg, Runnable r) {
        JButton b = navBtn(label, () -> show(key));
        nav.put(key, b); ref.put(key, r); content.add(pg, key); side.add(b); side.add(Box.createVerticalStrut(4));
    }
    static void show(String k) {
        cl.show(content, k);
        nav.forEach((name, b) -> { boolean on = name.equals(k); b.putClientProperty("on", on); b.setFont(on ? FB : F); b.repaint(); });
        ref.get(k).run();
    }

    // ================= login =================
    static void login() {
        JFrame f = new JFrame("Employee Management System - Login"); frame = f;
        f.setSize(1000, 700); f.setMinimumSize(new Dimension(820, 620)); f.setLocationRelativeTo(null); f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel bg = new JPanel(new GridBagLayout()) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); aa(g2); int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(24, 35, 74), w, h, new Color(59, 100, 219))); g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(255, 255, 255, 18)); g2.fillOval(-130, -150, 420, 420);
                g2.setColor(new Color(255, 255, 255, 14)); g2.fillOval(w - 260, h - 300, 480, 480);
                g2.setColor(new Color(255, 255, 255, 10)); g2.fillOval(w / 2 - 100, -70, 240, 240); g2.dispose();
            }
        };
        f.setContentPane(bg);

        JPanel head = new JPanel(); head.setOpaque(false); head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel logo = lbl("EMS", new Font("Segoe UI", 1, 42), Color.WHITE), title = lbl("Employee Management System", new Font("Segoe UI", 1, 20), Color.WHITE),
                sub = lbl("Manage employees, attendance, leave, payroll & tasks", F, new Color(220, 228, 250));
        JPanel line = new JPanel(); line.setBackground(BLUE); line.setPreferredSize(new Dimension(64, 4)); line.setMaximumSize(new Dimension(64, 4));
        for (JComponent c : new JComponent[]{logo, line, title, sub}) c.setAlignmentX(0.5f);
        title.setBorder(new EmptyBorder(16, 0, 4, 0));
        head.add(logo); head.add(Box.createVerticalStrut(12)); head.add(line); head.add(title); head.add(sub);

        JPanel card = new JPanel(new GridBagLayout()) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); aa(g2); int w = getWidth() - 14, h = getHeight() - 14;
                for (int i = 14; i >= 1; i--) { g2.setColor(new Color(15, 23, 42, Math.min(3 + i * 2, 45))); g2.fillRoundRect(i, i, w, h, 24, 24); }
                g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, w, h, 24, 24); g2.dispose();
            }
        };
        card.setOpaque(false); card.setBorder(new EmptyBorder(0, 0, 14, 14));
        Form fm = new Form(); fm.setBorder(new EmptyBorder(36, 40, 36, 40));
        JLabel w1 = lbl("Welcome Back", FT, DARK), w2 = lbl("Sign in to continue", F, MUTED);
        w1.setAlignmentX(0); w2.setAlignmentX(0); w2.setBorder(new EmptyBorder(4, 0, 18, 0));
        fm.add(w1); fm.add(w2);
        JTextField uf = fm.tf("Username"); JPasswordField pw = fm.pf("Password");
        Runnable go = () -> {
            String un = uf.getText().trim(), p = new String(pw.getPassword());
            if (un.isEmpty() || p.isEmpty()) { warn("Enter username and password."); return; }
            List<Object[]> r = q("SELECT password_hash,role,emp_id FROM users WHERE username=?", un);
            if (r.isEmpty() || !r.get(0)[0].equals(sha(p))) { warn("Invalid username or password."); pw.setText(""); return; }
            role = (String) r.get(0)[1]; empId = (Integer) r.get(0)[2]; user = un; f.dispose(); app();
        };
        pw.addActionListener(e -> go.run());
        fm.btns(btn("Sign In", BLUE, go));
        fm.note("Default admin login \u2014 admin / admin123");
        card.add(fm);

        GridBagConstraints g = new GridBagConstraints(); g.gridx = 0; g.insets = new Insets(0, 0, 30, 0); bg.add(head, g);
        g.insets = new Insets(0, 0, 0, 0); bg.add(card, g);
        f.setVisible(true);
    }

    // ================= app shell =================
    static void app() {
        frame = new JFrame("Employee Management System"); frame.setSize(1200, 750); frame.setMinimumSize(new Dimension(1000, 650));
        frame.setLocationRelativeTo(null); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cl = new CardLayout(); content = new JPanel(cl); content.setBackground(BG);
        side = new JPanel(); side.setBackground(NAVY); side.setPreferredSize(new Dimension(240, 0));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS)); side.setBorder(new EmptyBorder(24, 0, 20, 0));
        nav = new LinkedHashMap<>(); ref = new HashMap<>();
        JLabel logo = lbl("  EMS", new Font("Segoe UI", 1, 26), Color.WHITE); logo.setAlignmentX(0); logo.setBorder(new EmptyBorder(0, 16, 30, 0)); side.add(logo);

        dash(); if (adm()) emps(); att(); leave(); pay(); tasks();

        side.add(Box.createVerticalGlue()); side.add(navBtn("Logout", () -> { frame.dispose(); login(); }));
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(Color.WHITE); top.setPreferredSize(new Dimension(0, 60));
        top.setBorder(new MatteBorder(0, 0, 1, 0, LINE)); top.add(lbl("   Welcome, " + user + "  \u00b7  " + role, FB, DARK), BorderLayout.WEST);
        frame.add(side, BorderLayout.WEST); frame.add(top, BorderLayout.NORTH); frame.add(content);
        frame.setVisible(true); show("dash");
    }

    // ================= dashboard =================
    static void stat(JPanel row, String label, String val, Color c) {
        JPanel k = card(new BorderLayout()), in = new JPanel(), bar = new JPanel();
        k.setBorder(new EmptyBorder(20, 20, 20, 20)); in.setOpaque(false); in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
        bar.setBackground(c); bar.setPreferredSize(new Dimension(60, 5)); bar.setMaximumSize(new Dimension(60, 5)); bar.setAlignmentX(0);
        JLabel v = lbl(val, new Font("Segoe UI", 1, 30), DARK), t = lbl(label, F, MUTED); v.setAlignmentX(0); t.setAlignmentX(0);
        in.add(bar); in.add(Box.createVerticalStrut(14)); in.add(v); in.add(Box.createVerticalStrut(6)); in.add(t);
        k.add(in, BorderLayout.NORTH); row.add(k);
    }
    static void dash() {
        JPanel row = new JPanel(new GridLayout(1, 3, 20, 20)), wrap = new JPanel(new BorderLayout());
        row.setOpaque(false); wrap.setOpaque(false); wrap.add(row, BorderLayout.NORTH);
        Runnable r = () -> {
            row.removeAll();
            if (adm()) {
                stat(row, "Active Employees", "" + n("SELECT COUNT(*) FROM employee WHERE status='Active'"), BLUE);
                stat(row, "Present Today", "" + n("SELECT COUNT(*) FROM attendance WHERE date=CURDATE() AND status='Present'"), GREEN);
                stat(row, "Pending Leave Requests", "" + n("SELECT COUNT(*) FROM leave_request WHERE status='Pending'"), AMBER);
            } else {
                int id = empId == null ? -1 : empId;
                List<Object[]> a = q("SELECT check_out FROM attendance WHERE emp_id=? AND date=CURDATE()", id);
                stat(row, "Today's Attendance", a.isEmpty() ? "Not Checked In" : a.get(0)[0] == null ? "Checked In" : "Checked Out", BLUE);
                stat(row, "My Tasks", "" + n("SELECT COUNT(*) FROM task WHERE emp_id=?", id), GREEN);
                stat(row, "My Leave Requests", "" + n("SELECT COUNT(*) FROM leave_request WHERE emp_id=?", id), AMBER);
            }
            row.revalidate(); row.repaint();
        };
        add("dash", "Dashboard", page("Dashboard", wrap, null), r);
    }

    // ================= employees (Admin/HR) =================
    static void emps() {
        JTable t = tbl("ID", "Name", "Email", "Phone", "Department", "Designation", "Joined", "Status");
        Runnable r = () -> fill(t, q("SELECT e.emp_id,CONCAT(e.first_name,' ',e.last_name),e.email,e.phone,d.dept_name,e.designation,e.date_of_joining,e.status "
                + "FROM employee e LEFT JOIN department d ON e.dept_id=d.dept_id ORDER BY e.emp_id"));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); bar.setOpaque(false);
        bar.add(btn("Edit Selected", NAVY2, () -> { int id = sel(t); if (id > 0) empForm(id, r); }));
        bar.add(btn("Toggle Active / Inactive", RED, () -> { int id = sel(t); if (id > 0) { u("UPDATE employee SET status=IF(status='Active','Inactive','Active') WHERE emp_id=?", id); r.run(); } }));
        bar.add(btn("Create Login", GREEN, () -> { int id = sel(t); if (id > 0) loginForm(id); }));
        JPanel body = new JPanel(new BorderLayout()); body.setOpaque(false); body.add(tcard(t)); body.add(bar, BorderLayout.SOUTH);
        add("emp", "Employees", page("Employees", body, btn("+ Add Employee", BLUE, () -> empForm(0, r))), r);
    }
    static void empForm(int id, Runnable r) {
        Form f = new Form();
        JTextField fn = f.tf("First Name"), ln = f.tf("Last Name"), em = f.tf("Email"), ph = f.tf("Phone");
        JComboBox<Item> dp = new JComboBox<>(items("SELECT dept_id,dept_name FROM department ORDER BY dept_name")); f.row("Department", dp);
        JTextField ds = f.tf("Designation"), dj = f.tf("Date of Joining (YYYY-MM-DD)");
        JComboBox<String> st = new JComboBox<>(new String[]{"Active", "Inactive"}); f.row("Status", st);
        if (id > 0) {
            Object[] e = q("SELECT first_name,last_name,email,phone,dept_id,designation,date_of_joining,status FROM employee WHERE emp_id=?", id).get(0);
            fn.setText(s(e[0])); ln.setText(s(e[1])); em.setText(s(e[2])); ph.setText(s(e[3])); ds.setText(s(e[5])); dj.setText(s(e[6])); st.setSelectedItem(e[7]);
            for (int i = 0; i < dp.getItemCount(); i++) if (e[4] != null && dp.getItemAt(i).id() == ((Number) e[4]).intValue()) dp.setSelectedIndex(i);
        }
        f.btns(btn(id > 0 ? "Save Changes" : "Add Employee", BLUE, () -> {
            if (fn.getText().trim().isEmpty() || ln.getText().trim().isEmpty()) { warn("First and last name are required."); return; }
            Item d = (Item) dp.getSelectedItem(); if (d == null) { warn("Add a department first."); return; }
            java.sql.Date dt; try { dt = java.sql.Date.valueOf(dj.getText().trim()); } catch (IllegalArgumentException x) { warn("Date must be YYYY-MM-DD."); return; }
            Object[] v = {fn.getText().trim(), ln.getText().trim(), em.getText().trim(), ph.getText().trim(), d.id(), ds.getText().trim(), dt, st.getSelectedItem(), id};
            boolean ok = id > 0
                    ? u("UPDATE employee SET first_name=?,last_name=?,email=?,phone=?,dept_id=?,designation=?,date_of_joining=?,status=? WHERE emp_id=?", v)
                    : u("INSERT INTO employee (first_name,last_name,email,phone,dept_id,designation,date_of_joining,status) VALUES (?,?,?,?,?,?,?,?)", Arrays.copyOf(v, 8));
            if (ok) { close(f); r.run(); } else warn("Could not save. Check the console.");
        }), btn("Cancel", MUTED, () -> close(f)));
        dlg(id > 0 ? "Edit Employee" : "Add Employee", f, 480, 640).setVisible(true);
    }
    static void loginForm(int id) {
        Form f = new Form();
        JLabel who = lbl("Employee: " + s(q("SELECT CONCAT(first_name,' ',last_name) FROM employee WHERE emp_id=?", id).get(0)[0]) + " (#" + id + ")", FB, DARK);
        who.setAlignmentX(0); f.add(who);
        List<Object[]> ex = q("SELECT username FROM users WHERE emp_id=? LIMIT 1", id);
        if (!ex.isEmpty()) f.note("Note: this employee already has a login (\"" + ex.get(0)[0] + "\"). Creating another is allowed but unusual.");
        JTextField un = f.tf("Username"); JPasswordField pw = f.pf("Password"), cf = f.pf("Confirm Password");
        JComboBox<String> rl = new JComboBox<>(new String[]{"Employee", "HR", "Admin"}); f.row("Role", rl);
        f.btns(btn("Create Login", BLUE, () -> {
            String name = un.getText().trim(), p = new String(pw.getPassword());
            if (name.isEmpty()) { warn("Enter a username."); return; }
            if (p.length() < 4) { warn("Password must be at least 4 characters."); return; }
            if (!p.equals(new String(cf.getPassword()))) { warn("Passwords do not match."); return; }
            if (!q("SELECT 1 FROM users WHERE username=?", name).isEmpty()) { warn("Username already taken."); return; }
            if (u("INSERT INTO users (username,password_hash,role,emp_id) VALUES (?,?,?,?)", name, sha(p), rl.getSelectedItem(), id)) { info("Login created for \"" + name + "\"."); close(f); }
            else warn("Could not create login. Check the console.");
        }), btn("Cancel", MUTED, () -> close(f)));
        dlg("Create Login", f, 440, 520).setVisible(true);
    }

    // ================= attendance =================
    static void att() {
        if (adm()) {
            JTable t = tbl("ID", "Employee", "Date", "Check In", "Check Out", "Status");
            add("att", "Attendance", page("Attendance", tcard(t), null), () -> fill(t, q("SELECT a.attendance_id,CONCAT(e.first_name,' ',e.last_name),a.date,a.check_in,IFNULL(a.check_out,'--'),a.status "
                    + "FROM attendance a JOIN employee e ON a.emp_id=e.emp_id WHERE a.date=CURDATE() ORDER BY e.first_name")));
            return;
        }
        int id = empId == null ? -1 : empId;
        JTable t = tbl("Date", "Check In", "Check Out", "Status");
        JLabel st = lbl("", new Font("Segoe UI", 1, 16), DARK); st.setAlignmentX(0);
        JButton ci = btn("Check In", GREEN, () -> { u("INSERT INTO attendance (emp_id,date,check_in,status) VALUES (?,CURDATE(),CURTIME(),'Present')", id); ref.get("att").run(); });
        JButton co = btn("Check Out", RED, () -> { u("UPDATE attendance SET check_out=CURTIME() WHERE emp_id=? AND date=CURDATE()", id); ref.get("att").run(); });
        Runnable r = () -> {
            List<Object[]> a = q("SELECT check_in,check_out FROM attendance WHERE emp_id=? AND date=CURDATE()", id);
            st.setText(a.isEmpty() ? "You have not checked in today." : a.get(0)[1] == null ? "Checked in at " + a.get(0)[0] : "Completed: " + a.get(0)[0] + " to " + a.get(0)[1]);
            ci.setEnabled(a.isEmpty()); co.setEnabled(!a.isEmpty() && a.get(0)[1] == null);
            fill(t, q("SELECT date,check_in,IFNULL(check_out,'--'),status FROM attendance WHERE emp_id=? ORDER BY date DESC", id));
        };
        JPanel br = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); br.setOpaque(false); br.setAlignmentX(0); br.add(ci); br.add(co);
        JPanel in = new JPanel(); in.setOpaque(false); in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
        in.add(st); in.add(Box.createVerticalStrut(14)); in.add(br);
        JPanel ac = card(new BorderLayout()); ac.add(in);
        JPanel body = new JPanel(new BorderLayout(0, 20)); body.setOpaque(false); body.add(ac, BorderLayout.NORTH); body.add(tcard(t));
        add("att", "Attendance", page("Attendance", body, null), r);
    }

    // ================= leave =================
    static void leave() {
        if (adm()) {
            JTable t = tbl("ID", "Employee", "From", "To", "Reason", "Status");
            Runnable r = () -> fill(t, q("SELECT l.leave_id,CONCAT(e.first_name,' ',e.last_name),l.from_date,l.`to_date`,l.reason,l.status "
                    + "FROM leave_request l JOIN employee e ON l.emp_id=e.emp_id WHERE l.status='Pending' ORDER BY l.leave_id"));
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); bar.setOpaque(false);
            bar.add(btn("Approve Selected", GREEN, () -> { int id = sel(t); if (id > 0) { u("UPDATE leave_request SET status='Approved' WHERE leave_id=?", id); r.run(); } }));
            bar.add(btn("Reject Selected", RED, () -> { int id = sel(t); if (id > 0) { u("UPDATE leave_request SET status='Rejected' WHERE leave_id=?", id); r.run(); } }));
            JPanel body = new JPanel(new BorderLayout()); body.setOpaque(false); body.add(tcard(t)); body.add(bar, BorderLayout.SOUTH);
            add("leave", "Leave Requests", page("Leave Requests", body, null), r);
            return;
        }
        int id = empId == null ? -1 : empId;
        JTable t = tbl("From", "To", "Reason", "Status");
        Form f = new Form();
        JTextField a = f.tf("From (YYYY-MM-DD)"), b = f.tf("To (YYYY-MM-DD)"), why = f.tf("Reason");
        Runnable r = () -> fill(t, q("SELECT from_date,`to_date`,reason,status FROM leave_request WHERE emp_id=? ORDER BY leave_id DESC", id));
        f.btns(btn("Submit Request", BLUE, () -> {
            try {
                if (why.getText().trim().isEmpty()) { warn("Please give a reason."); return; }
                u("INSERT INTO leave_request (emp_id,from_date,`to_date`,reason,status) VALUES (?,?,?,?,'Pending')", id,
                        java.sql.Date.valueOf(a.getText().trim()), java.sql.Date.valueOf(b.getText().trim()), why.getText().trim());
                clr(a, b, why); r.run();
            } catch (IllegalArgumentException x) { warn("Dates must be YYYY-MM-DD."); }
        }), btn("Clear", MUTED, () -> clr(a, b, why)));
        add("leave", "Leave Requests", page("Leave Requests", stack(f, t), null), r);
    }

    // ================= payroll =================
    static void pay() {
        if (!adm()) {
            int id = empId == null ? -1 : empId;
            JTable t = tbl("Month", "Basic", "Allowances", "Deductions", "Net Salary", "Date");
            add("pay", "Payroll", page("Payroll", tcard(t), null), () -> fill(t, q("SELECT month,basic_salary,allowances,deductions,net_salary,generated_date FROM payroll WHERE emp_id=? ORDER BY generated_date DESC", id)));
            return;
        }
        JTable t = tbl("ID", "Employee", "Month", "Basic", "Allowances", "Deductions", "Net Salary", "Date");
        Form f = new Form();
        JComboBox<Item> who = new JComboBox<>(people()); f.row("Employee", who);
        JTextField mo = f.tf("Month (e.g. 2026-09)"), bs = f.tf("Basic Salary"), al = f.tf("Allowances"), de = f.tf("Deductions");
        Runnable r = () -> {
            who.setModel(new DefaultComboBoxModel<>(people()));
            fill(t, q("SELECT p.payroll_id,CONCAT(e.first_name,' ',e.last_name),p.month,p.basic_salary,p.allowances,p.deductions,p.net_salary,p.generated_date "
                    + "FROM payroll p JOIN employee e ON p.emp_id=e.emp_id ORDER BY p.generated_date DESC,p.payroll_id DESC"));
        };
        f.btns(btn("Generate Payslip", BLUE, () -> {
            Item e = (Item) who.getSelectedItem(); if (e == null) { warn("No employee selected."); return; }
            try {
                double b = Double.parseDouble(bs.getText().trim()), a = Double.parseDouble(al.getText().trim()), d = Double.parseDouble(de.getText().trim());
                u("INSERT INTO payroll (emp_id,month,basic_salary,allowances,deductions,net_salary,generated_date) VALUES (?,?,?,?,?,?,CURDATE())", e.id(), mo.getText().trim(), b, a, d, b + a - d);
                clr(mo, bs, al, de); r.run();
            } catch (NumberFormatException x) { warn("Basic, Allowances and Deductions must be numbers."); }
        }), btn("Clear", MUTED, () -> clr(mo, bs, al, de)));
        add("pay", "Payroll", page("Payroll", stack(f, t), null), r);
    }

    // ================= tasks =================
    static void tasks() {
        if (!adm()) {
            int id = empId == null ? -1 : empId;
            JTable t = tbl("ID", "Title", "Description", "Deadline", "Status");
            Runnable r = () -> fill(t, q("SELECT task_id,title,description,deadline,status FROM task WHERE emp_id=? ORDER BY deadline", id));
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); bar.setOpaque(false);
            for (String[] s : new String[][]{{"Mark In Progress", "In Progress"}, {"Mark Done", "Done"}})
                bar.add(btn(s[0], s[1].equals("Done") ? GREEN : AMBER, () -> { int tid = sel(t); if (tid > 0) { u("UPDATE task SET status=? WHERE task_id=? AND emp_id=?", s[1], tid, id); r.run(); } }));
            JPanel body = new JPanel(new BorderLayout()); body.setOpaque(false); body.add(tcard(t)); body.add(bar, BorderLayout.SOUTH);
            add("tasks", "Tasks", page("Tasks", body, null), r);
            return;
        }
        JTable t = tbl("ID", "Employee", "Title", "Deadline", "Status");
        Form f = new Form();
        JComboBox<Item> who = new JComboBox<>(people()); f.row("Employee", who);
        JTextField ti = f.tf("Task Title");
        JTextArea ta = fld(new JTextArea(3, 20)); ta.setLineWrap(true); ta.setWrapStyleWord(true);
        JScrollPane sp = f.row("Description", new JScrollPane(ta)); sp.setBorder(null); sp.setPreferredSize(new Dimension(320, 80)); sp.setMaximumSize(new Dimension(460, 80));
        JTextField dl = f.tf("Deadline (YYYY-MM-DD)");
        Runnable r = () -> {
            who.setModel(new DefaultComboBoxModel<>(people()));
            fill(t, q("SELECT t.task_id,CONCAT(e.first_name,' ',e.last_name),t.title,t.deadline,t.status FROM task t JOIN employee e ON t.emp_id=e.emp_id ORDER BY t.deadline"));
        };
        f.btns(btn("Assign Task", BLUE, () -> {
            Item e = (Item) who.getSelectedItem(); if (e == null) { warn("No employee selected."); return; }
            if (ti.getText().trim().isEmpty()) { warn("Enter a task title."); return; }
            try {
                u("INSERT INTO task (emp_id,title,description,deadline,status) VALUES (?,?,?,?,'Pending')", e.id(), ti.getText().trim(), ta.getText().trim(), java.sql.Date.valueOf(dl.getText().trim()));
                clr(ti, ta, dl); r.run();
            } catch (IllegalArgumentException x) { warn("Deadline must be YYYY-MM-DD."); }
        }), btn("Clear", MUTED, () -> clr(ti, ta, dl)));
        add("tasks", "Tasks", page("Tasks", stack(f, t), null), r);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { /* keep default look */ }
            login();
        });
    }
}
