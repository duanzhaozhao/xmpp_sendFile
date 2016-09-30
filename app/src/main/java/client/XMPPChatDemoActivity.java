package client;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import client.myxmpp.R;

//asmack-yxl-1.0 下载网址 http://www.eoeandroid.com/thread-186418-1-1.html
public class XMPPChatDemoActivity extends Activity {

	public static final String HOST = "10.8.2.234";
	public static final int PORT = 5222;
	public static final String SERVICE = "10.8.2.234";
	public static final String USERNAME = "123456";
	public static final String PASSWORD = "123456";

	private XMPPConnection connection;
	private ArrayList<String> messages = new ArrayList<String>();
	private Handler mHandler = new Handler();

	private EditText recipient;
	private EditText textMessage;
	private ListView listview;
	private ImageView sendFile;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		recipient = (EditText) this.findViewById(R.id.toET);
		textMessage = (EditText) this.findViewById(R.id.chatET);
		sendFile = (ImageView) findViewById(R.id.sendFile);
		listview = (ListView) this.findViewById(R.id.listMessages);
		setListAdapter();

		// Set a listener to send a chat text message
		Button send = (Button) this.findViewById(R.id.sendBtn);
		send.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				String to = recipient.getText().toString();
				String text = textMessage.getText().toString();

				Log.i("XMPPChatDemoActivity", "Sending text " + text + " to " + to);
				Message msg = new Message(to+"@10.8.2.234", Message.Type.chat);//to username@10.8.2.234
				msg.setBody(text);				
				if (connection != null) {
					connection.sendPacket(msg);
					messages.add(connection.getUser() + ":");//123456@10.8.2.234/Smack
					messages.add(text);
					setListAdapter();
				}
			}
		});

		sendFile.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setAction(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				startActivityForResult(intent,1);
			}
		});

		connect();

	}


	/**
	 * Called by Settings dialog when a connection is establised with the XMPP
	 * server
	 * 
	 * @param connection
	 */
	public void setConnection(XMPPConnection connection) {
		this.connection = connection;
		if (connection != null) {
			// Add a packet listener to get messages sent to us
			PacketFilter filter = new MessageTypeFilter(Message.Type.chat);//过滤接收的Message
			connection.addPacketListener(new PacketListener() {
				@Override
				public void processPacket(Packet packet) {
					Message message = (Message) packet;
					if (message.getBody() != null) {
						String fromName = StringUtils.parseBareAddress(message
								.getFrom());
						Log.i("XMPPChatDemoActivity", "Text Recieved " + message.getBody()
								+ " from " + fromName );//from :lan@10.8.2.234
						messages.add(fromName + ":");
						messages.add(message.getBody());
						// Add the incoming message to the list view
						mHandler.post(new Runnable() {
							public void run() {
								setListAdapter();
							}
						});
					}
				}
			}, filter);
		}
	}

	private void setListAdapter() {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				R.layout.listitem, messages);
		listview.setAdapter(adapter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			if (connection != null)
				connection.disconnect();
		} catch (Exception e) {

		}
	}

	public void connect() {

		final ProgressDialog dialog = ProgressDialog.show(this,
				"Connecting...", "Please wait...", false);

		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				// Create a connection
				ConnectionConfiguration connConfig = new ConnectionConfiguration(
						HOST, PORT, SERVICE);
				connConfig.setReconnectionAllowed(true);
				connConfig.setSendPresence(true);
				XMPPConnection connection = new XMPPConnection(connConfig);
//给接收文件注册监听器
				receiveFile(connection);
				try {
					connection.connect();
					Log.i("XMPPChatDemoActivity",
							"Connected to " + connection.getHost());
				} catch (XMPPException ex) {
					Toast.makeText(XMPPChatDemoActivity.this,"connect fail",Toast.LENGTH_SHORT).show();
					Log.e("XMPPChatDemoActivity", "Failed to connect to "
							+ connection.getHost());
					Log.e("XMPPChatDemoActivity", ex.toString());
					setConnection(null);
				}

				try {
					connection.getAccountManager().createAccount(USERNAME,PASSWORD);
				} catch (XMPPException e) {
					e.printStackTrace();
				}
				try {
					// SASLAuthentication.supportSASLMechanism("PLAIN", 0);
					connection.login(USERNAME, PASSWORD);
					Log.i("XMPPChatDemoActivity",
							"Logged in as " + connection.getUser());

					// Set the status to available
					Presence presence = new Presence(Presence.Type.available);
					connection.sendPacket(presence);
					setConnection(connection);

					Roster roster = connection.getRoster();
					Collection<RosterEntry> entries = roster.getEntries();//好友
					for (RosterEntry entry : entries) {
						Log.i("aaaa","aaa3");
						Log.d("XMPPChatDemoActivity",
								"--------------------------------------");
						Log.d("XMPPChatDemoActivity", "RosterEntry " + entry);
						Log.d("XMPPChatDemoActivity",
								"User: " + entry.getUser());
						Log.d("XMPPChatDemoActivity",
								"Name: " + entry.getName());
						Log.d("XMPPChatDemoActivity",
								"Status: " + entry.getStatus());
						Log.d("XMPPChatDemoActivity",
								"Type: " + entry.getType());
						Presence entryPresence = roster.getPresence(entry
								.getUser());
						Log.d("XMPPChatDemoActivity", "Presence Status: "
								+ entryPresence.getStatus());
						Log.d("XMPPChatDemoActivity", "Presence Type: "
								+ entryPresence.getType());
						Presence.Type type = entryPresence.getType();
						if (type == Presence.Type.available)
							Log.d("XMPPChatDemoActivity", "Presence AVIALABLE");
						Log.d("XMPPChatDemoActivity", "Presence : "
								+ entryPresence);

					}
				} catch (XMPPException ex) {
					Log.e("XMPPChatDemoActivity", "Failed to log in as "
							+ USERNAME);
					Log.e("XMPPChatDemoActivity", ex.toString());
					setConnection(null);
				}

				dialog.dismiss();
			}
		});
		t.start();
		dialog.show();
	}

//发送文件
	public void sendFile(XMPPConnection connection,
								String user, File file) throws XMPPException, InterruptedException {

		System.out.println("发送文件开始"+file.getName());
		FileTransferManager manager = new FileTransferManager(connection);
		OutgoingFileTransfer transfer = manager.createOutgoingFileTransfer(user+"@10.8.2.234/spark");
		try {
			transfer.sendFile(file, file.getName());
		} catch (XMPPException e) {
			e.printStackTrace();
		}
		while(!transfer.isDone()) {
			if(transfer.getStatus().equals(FileTransfer.Status.error)) {
				System.out.println("ERROR!!! " + transfer.getError());
				Log.i("aaaaa","ERROR!!! " + transfer.getError());
			} else if (transfer.getStatus().equals(FileTransfer.Status.cancelled)
					|| transfer.getStatus().equals(FileTransfer.Status.refused)) {
				System.out.println("Cancelled!!! " + transfer.getError());
				Log.i("aaaaa","Cancelled!!! " + transfer.getError());
			}
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(transfer.getStatus().equals(FileTransfer.Status.refused) || transfer.getStatus().equals(FileTransfer.Status.error)
				|| transfer.getStatus().equals(FileTransfer.Status.cancelled)){
			System.out.println("refused cancelled error " + transfer.getError());
			Log.i("aaaaa","refused cancelled error " + transfer.getError());
		} else {
			System.out.println("Success");
			Log.i("aaaaa","Success");
		}
		System.out.println("//////////");
		System.out.println(transfer.getStatus());//Initial
		System.out.println(transfer.getProgress());// 0.0
		System.out.println(transfer.isDone());//false

		System.out.println("//////////");

		System.out.println("发送文件结束");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK) {
			finish();
		} else if (requestCode == 1) {
			String mFilePath;
			mFilePath = Uri.decode(data.getDataString());
//通过data.getDataString()得到的路径如果包含中文路径，则会出现乱码现象，经过Uri.decode()函数进行解码，得到正确的路径。但是此时路径为Uri路径，必须转换为String路径，网上有很多方法，本人通过对比发现，Uri路径里多了file：//字符串，所以采用以下方法将前边带的字符串截取掉，获得String路径，可能通用性不够好，下一步会学习更好的方法。
			mFilePath = mFilePath.substring(7, mFilePath.length());
			final File file = new File(mFilePath);
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						sendFile(connection,"lan",file);
					} catch (XMPPException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
//					try {
//						sendNewFile("lan",file1,connection);
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
				}
			}).start();

		}
	}

	public class RecFileTransferListener implements FileTransferListener {

		@Override
		public void fileTransferRequest(final FileTransferRequest fileTransferRequest) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					System.out.println("接收文件开始.....");
			final IncomingFileTransfer inTransfer = fileTransferRequest.accept();
			final String fileName = fileTransferRequest.getFileName();
			long length = fileTransferRequest.getFileSize();
			final String fromUser = fileTransferRequest.getRequestor().split("/")[0];
			System.out.println("文件大小:"+length + "  "+fileTransferRequest.getRequestor());
			System.out.println(""+fileTransferRequest.getMimeType());

					IncomingFileTransfer transfer = fileTransferRequest.accept();
					try {
						transfer.recieveFile(new File(Environment.getExternalStorageDirectory(),
								"test.docx"));
					} catch (XMPPException e) {
						e.printStackTrace();
					}
					System.out.println("接收文件结束.....");
				}
			}).start();


		}
	}

//接收文件
	public void receiveFile(XMPPConnection connection){
		FileTransferManager transfer = new FileTransferManager(connection);
		transfer.addFileTransferListener(new RecFileTransferListener());
	}
	}
