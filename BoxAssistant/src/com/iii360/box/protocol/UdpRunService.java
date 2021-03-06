package com.iii360.box.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.iii.wifi.dao.info.WifiDeviceInfo;
import com.iii.wifi.dao.info.WifiDeviceInfos;
import com.iii360.box.MyApplication;
import com.iii360.box.common.BasePreferences;
import com.iii360.box.util.KeyList;
import com.iii360.box.util.LogManager;
import com.iii360.box.util.LogUtil;
import com.iii360.box.view.NotificationBar;

public class UdpRunService {
	private boolean mRuning = false;
	private DatagramSocket mDatagramSocket = null;
	private BasePreferences mPreferences;
	private DatagramPacket receivePacket;
	private WifiManager.MulticastLock mLock;
	static long i = 0;
	private static UdpRunService instance;

	public static UdpRunService getInstance(final Context context, final int port) {
		if (instance == null) {
			instance = new UdpRunService(context, port);
		}
		return instance;
	}

	private UdpRunService(final Context context, final int port) {
		mRuning = true;
		mPreferences = new BasePreferences(context);
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		mLock = manager.createMulticastLock("test wifi");

		Thread mReciverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					long i = 0;
					mLock.acquire();
					while (mRuning) {
						byte[] receiveData = new byte[1024 * 4];

						if (mDatagramSocket == null) {
							mDatagramSocket = new DatagramSocket(null);
							mDatagramSocket.setReuseAddress(true);
							mDatagramSocket.bind(new InetSocketAddress(port));
						}
						receivePacket = new DatagramPacket(receiveData, receiveData.length);
						mDatagramSocket.receive(receivePacket);
						String ip = receivePacket.getAddress().getHostAddress();
						String receiver = new String(receivePacket.getData(), 0, receivePacket.getLength());
						if (listener != null) {
							listener.onResult(ip);
						}
						if (!TextUtils.isEmpty(ip)) {
							// terry start
							// mPreferences.setPrefString(
							// KeyList.GKEY_BOX_IP_ADDRESS, ip);
							// terry end
							/**
							 * terry
							 */
							try {
								long receiveTime = System.currentTimeMillis();
								// if ("192.168.40.144".equals(ip)) {
								// Log.d("info", "本次udp与上次的时差:" + (receiveTime -
								// i));
								// i = receiveTime;
								// }
								Map<String, Long> adds = MyApplication.getBoxAdds();
								Map<String, String> serialNums = MyApplication.getSerialNums();
								// try {
								// if (!adds.containsKey(ip)) {
								// // Log.i("terry", "new box");
								// sendBroadCastNewbox(context);
								// }
								// } catch (Exception e) {
								// e.printStackTrace();
								// }
								adds.put(ip, receiveTime);
								// smallzhi_apip::192.168.40.144SerialNumberStart=SZA0A2507C8Y=SerialNumberEndend::
								if (receiver != null && receiver.contains("SerialNumberStart=")) {
									String data = receiver.split("SerialNumberStart=")[1];
									serialNums.put(ip, data.substring(0, data.indexOf("=SerialNumberEnd")));
								}
								// Log.i("info", ""+serialNums);
							} catch (Exception e) {
								e.printStackTrace();
							}

							/**
							 * terry
							 */
						}
						sendBoradcast(context, receiver, ip);
						LogUtil.i("--------" + ip + "---------" + receiver);
					}
					mLock.release();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		mReciverThread.start();
	}

	// private Intent intent = new Intent(KeyList.AKEY_NEW_DEVICE_LIST_ACTION);
	public NotificationBar mNotificationBar;
	/**
	 * 新增设备列表
	 */
	private List<WifiDeviceInfo> mAddList = new ArrayList<WifiDeviceInfo>();
	/**
	 * 去除设备列表
	 */
	private List<WifiDeviceInfo> mDeleteList = new ArrayList<WifiDeviceInfo>();

	/**
	 * 比较没有配置设备列表
	 * 
	 * @param lastData
	 *            上一次显示的列表数据
	 * @param currentData
	 *            当前获取到的列表数据
	 */
	private void compareDevice(String lastData, String currentData) {
		if (lastData.equals(currentData)) {
			mPreferences.setPrefBoolean(KeyList.IKEY_PUSH_NEW_DEVICE_SWTICH, true);

		} else {

			WifiDeviceInfos lastInfo = new Gson().fromJson(lastData, WifiDeviceInfos.class);
			WifiDeviceInfos currentInfo = new Gson().fromJson(currentData, WifiDeviceInfos.class);

			List<WifiDeviceInfo> list1 = lastInfo.getWifiInfo();
			List<WifiDeviceInfo> list2 = currentInfo.getWifiInfo();

			mAddList.clear();
			mDeleteList.clear();

			// 旧数据和新数据比较，如果新数据中有，旧数据没有，则说明有新的设备添加了
			for (int i = 0; i < list2.size(); i++) {
				// 判断列表中是否存在
				if (!isExist(list2.get(i), list1)) {
					mAddList.add(list2.get(i));
				}
			}
			// 旧数据和新数据比较，如果旧数据有，新数据中没有，则说明有设备不在线了
			for (int i = 0; i < list1.size(); i++) {
				if (!isExist(list1.get(i), list2)) {
					mDeleteList.add(list1.get(i));
				}
			}

			if (mAddList.isEmpty() && mDeleteList.isEmpty()) {
				mPreferences.setPrefBoolean(KeyList.IKEY_PUSH_NEW_DEVICE_SWTICH, true);
			}
		}

	}

	private boolean isExist(WifiDeviceInfo info, List<WifiDeviceInfo> list) {
		String mac = info.getMacadd();

		for (int i = 0; i < list.size(); i++) {

			if (mac.equals(list.get(i).getMacadd())) {

				return true;
			}
		}
		return false;
	}

	private class HandleUdpDeviceTask implements Runnable {
		private String ip;
		private String receiver;
		private Context context;

		public HandleUdpDeviceTask(String ip, String receiver, Context context) {
			this.ip = ip;
			this.receiver = receiver;
			this.context = context;
		}

		public void run() {
			try {
				String ip = this.ip;
				String gsonEntity = receiver;
				LogUtil.d("主线程处理" + ip + "," + receiver + "end");
				LogManager.e("ip=" + ip + "||" + mPreferences.getPrefString(KeyList.GKEY_BOX_IP_ADDRESS));
				if (!ip.equals(mPreferences.getPrefString(KeyList.GKEY_BOX_IP_ADDRESS))) {
					return;
				}
				LogManager.e("" + gsonEntity);
				String lastGsonEntity = mPreferences.getPrefString(KeyList.IKEY_NEW_DEVICE_GSON_LIST);
				mPreferences.setPrefString(KeyList.IKEY_NEW_DEVICE_GSON_LIST, gsonEntity);
				mNotificationBar = NotificationBar.getInstance(context);

				if (TextUtils.isEmpty(gsonEntity)) {
					if (mNotificationBar != null) {
						mNotificationBar.dismiss();
					}
					return;
				}

				// LogManager.d("==================================");
				// LogManager.d("lastGsonEntity=" + lastGsonEntity);
				// LogManager.d("gsonEntity=" + gsonEntity);
				// LogManager.d("same =" + gsonEntity.equals(lastGsonEntity));

				if (gsonEntity.equals(lastGsonEntity)) {
					return;
				}

				WifiDeviceInfos infos = new Gson().fromJson(gsonEntity, WifiDeviceInfos.class);
				WifiDeviceInfo info = infos.getWifiInfo().get(0);

				// 没有显示过，显示一次
				if (TextUtils.isEmpty(lastGsonEntity)) {
					// LogManager.d("没有显示过，显示一次");
					mNotificationBar.showNewDevice(info);
					mPreferences.setPrefString(KeyList.IKEY_SHOW_NEW_DEVICE_MAC, info.getMacadd());

					return;
				}

				compareDevice(lastGsonEntity, gsonEntity);

				// LogManager.i("GlobalReceiver Notification show :" +
				// !mPreferences.getPrefBoolean(KeyList.IKEY_PUSH_NEW_DEVICE_SWTICH,
				// false));

				// 已经显示了，就不在显示
				if (!mPreferences.getPrefBoolean(KeyList.IKEY_PUSH_NEW_DEVICE_SWTICH, false)) {
					// LogManager.d("已经显示了，就不在显示 ");
					mNotificationBar.showNewDevice(info);
					mPreferences.setPrefString(KeyList.IKEY_SHOW_NEW_DEVICE_MAC, info.getMacadd());
				}

				LogManager.d("GlobalReceiver mDeleteList size=" + mDeleteList.size());
				LogManager.d("GlobalReceiver mAddList size=" + mAddList.size());

				// 如果移除列表有数据，则移除状态栏上显示的
				if (!mDeleteList.isEmpty()) {
					String mac = mPreferences.getPrefString(KeyList.IKEY_SHOW_NEW_DEVICE_MAC);
					if (!TextUtils.isEmpty(mac)) {
						for (int i = 0; i < mDeleteList.size(); i++) {
							LogManager.i("mDeleteList mac :getMacadd()  " + mac + "===" + mDeleteList.get(i).getMacadd());
							if (mDeleteList.get(i).getMacadd().equals(mac)) {
								LogManager.e("Notification dismiss");
								mNotificationBar.dismiss();
							}
						}
					}
					mDeleteList.clear();
				}

				// 如果添加列表有数据，则在状态栏上显示的
				if (!mAddList.isEmpty()) {
					// LogManager.d("添加列表有数据，则在状态栏上显示的");
					mNotificationBar.showNewDevice(mAddList.get(0));
					mPreferences.setPrefString(KeyList.IKEY_SHOW_NEW_DEVICE_MAC, mAddList.get(0).getMacadd());
					mAddList.clear();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	};

	private void sendBoradcast(Context context, String receiver, String ip) {
		// terry
		// context.sendBroadcast(new Intent(KeyList.AKEY_UDP_BRODCAST));
		if (KeyList.AKEY_MUSIC_STATUS_CHANGE.equals(receiver)) {
			Intent intent = new Intent(KeyList.ACTION_MUSIC_STATUS_CHANGE);
			context.sendBroadcast(intent);
		} else if (receiver.startsWith(KeyList.ACTION_DEVIDE_LIST_HEAD)) {
			long startTime = mPreferences.getPrefLong(KeyList.PKEY_UNCONFIG_DEVICE_TIME);
			long disTime = System.currentTimeMillis() - startTime;
			LogUtil.d("udp收到" + receiver);
			// if (disTime > 1000) {
			mPreferences.setPrefLong(KeyList.PKEY_UNCONFIG_DEVICE_TIME, System.currentTimeMillis());
			// 没有配置设备列表
			receiver = receiver.substring(KeyList.ACTION_DEVIDE_LIST_HEAD.length(), receiver.length());
			// intent.putExtra(KeyList.KEY_DEVICE_IP, ip);
			// intent.putExtra(KeyList.IKEY_NEW_DEVICE_LIST, receiver);
			// context.sendBroadcast(intent);
			new Handler(Looper.getMainLooper()).post(new HandleUdpDeviceTask(ip, receiver, context));
			// }

		} else if (KeyList.PKEY_FIND_DEVICE_TAG.equals(receiver) || KeyList.PKEY_REMOVE_DEVICE_TAG.equals(receiver)) {
			// 发现配件变更
			// LogManager.e("发现配件变更,发送广播。。。");
			// context.sendBroadcast(new
			// Intent(KeyList.AKEY_CHECK_DEVICE_BRODCAST));

		} else if (KeyList.ACTION_MUSIC_STOP.equals(receiver)) {
			// 停止播放音乐
			LogManager.e("停止播放音乐");
			mPreferences.setPrefString(KeyList.PKEY_SELECT_MUSIC_ID, "-1");
		}
	}

	public void disConnect() {
		mRuning = false;
		if (mDatagramSocket == null)
			return;
		try {
			mDatagramSocket.disconnect();
			mDatagramSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private UdpListener listener;

	public void setListener(UdpListener listener) {
		this.listener = listener;
	}

	public interface UdpListener {
		public void onResult(String ip);
	}
}
