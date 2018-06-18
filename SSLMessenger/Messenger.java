import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.awt.event.ActionEvent;
import javax.swing.JTextArea;
import java.awt.Color;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class Messenger extends JFrame {

	private JPanel contentPane;
	public JTextField sendingTextField;
	public JTextArea receivingTextArea;
	public JButton btnSendButton;
	public JLabel userNameLabel;
	public static MessengerClient client;
	public String id;
	public String ip;
	public int port;
	public static Messenger frame;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					JFrame frame1 = new JFrame("Input your ID");
				    String temp_id = JOptionPane.showInputDialog(frame1, "What's your name?");
				    JFrame frame2 = new JFrame("Input server IP");
				    String temp_ip = JOptionPane.showInputDialog(frame1, "What's the server IP?");
				   
					frame = new Messenger();
					frame.id = temp_id;
					frame.userNameLabel.setText(temp_id);
					frame.ip = temp_ip;
					
					frame.setVisible(true);
					
					client = new MessengerClient(frame.ip, 8700, frame);
					Thread t = new Thread(client);
					t.start();
					
					String idInfo="@userinfo@"+frame.id;					
					
					frame.sendingTextField.setText(idInfo);
					frame.btnSendButton.doClick();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		 Runtime.getRuntime().addShutdownHook(new Thread() {
	            public void run() {
	                try {
						client.closeConnection(client.channel, client.engine);
					} catch (IOException e) {
						e.printStackTrace();
					}
	            }
	     });
	}

	/**
	 * Create the frame.
	 */
	public Messenger() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 473, 532);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		sendingTextField = new JTextField();
		sendingTextField.setBounds(12, 402, 329, 64);
		contentPane.add(sendingTextField);
		sendingTextField.setColumns(10);
		sendingTextField.setText("");
		
		btnSendButton = new JButton("전송");
		btnSendButton.setBounds(353, 402, 92, 64);
		btnSendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				MessengerClientSender sender;
				try {
					sender = new MessengerClientSender(client.protocol,client.srvIP,8700, client.channel, client.engine, client.recvMsg, client.frame);
					Thread senderThread = new Thread(sender);
					senderThread.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		contentPane.add(btnSendButton);
		
		userNameLabel = new JLabel("user name");
		userNameLabel.setBounds(228, 0, 217, 31);
		userNameLabel.setFont(new Font("굴림", Font.BOLD, 13));
		userNameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		contentPane.add(userNameLabel);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBounds(12, 29, 433, 363);
		contentPane.add(scrollPane);
		
		receivingTextArea = new JTextArea();
		receivingTextArea.setEditable(false);
		receivingTextArea.setLineWrap(true);
		receivingTextArea.setWrapStyleWord(true);
		scrollPane.setViewportView(receivingTextArea);
	}
}
