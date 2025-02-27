package org.medicmobile.webapp.mobile;

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStoragePublicDirectory;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Process;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.DatePicker;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Locale.UK;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MedicAndroidJavascript {
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private final EmbeddedBrowserActivity parent;
	private final MrdtSupport mrdt;
	private final SmsSender smsSender;
	private final ChtExternalAppHandler chtExternalAppHandler;

	private ActivityManager activityManager;
	private ConnectivityManager connectivityManager;
	private Alert soundAlert;

	public MedicAndroidJavascript(EmbeddedBrowserActivity parent) {
		this.parent = parent;
		this.mrdt = parent.getMrdtSupport();
		this.smsSender = parent.getSmsSender();
		this.chtExternalAppHandler = parent.getChtExternalAppHandler();
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setActivityManager(ActivityManager activityManager) {
		this.activityManager = activityManager;
	}

	public void setConnectivityManager(ConnectivityManager connectivityManager) {
		this.connectivityManager = connectivityManager;
	}

	//> JavascriptInterface METHODS
	@android.webkit.JavascriptInterface
	public String getAppVersion() {
		try {
			return parent.getPackageManager()
					.getPackageInfo(parent.getPackageName(), 0)
					.versionName;
		} catch(Exception ex) {
			return jsonError("Error fetching app version: ", ex);
		}
	}

	@JavascriptInterface
	public void toastResult(String result){
		Toast.makeText(parent, result, Toast.LENGTH_LONG).show();
	}
	@JavascriptInterface
	public void saveDocs(String docs, String username) throws IOException{
		JSONArray newDocs = new JSONArray();
		try {
			docs = docs.replaceAll("\\\\/", "/");
			JSONObject docs_obj = new JSONObject(docs);
			JSONArray docs_list = docs_obj.getJSONArray("rows");
			for (int i = 0; i < docs_list.length(); i++) {
				docs_list.getJSONObject(i).remove("key");
				docs_list.getJSONObject(i).remove("value");
				docs_list.getJSONObject(i).getJSONObject("doc").remove("_rev");
				if(docs_list.getJSONObject(i).has("_rev")){
					docs_list.getJSONObject(i).remove("_rev");
				}
				if(docs_list.getJSONObject(i).getJSONObject("doc").has("doc")){
					docs_list.remove(i);
				}
				if (docs_list.getJSONObject(i).getString("id").startsWith("form")|| docs_list.getJSONObject(i).getString("id").equals("settings") || docs_list.getJSONObject(i).getString("id").startsWith("service") ||docs_list.getJSONObject(i).getString("id").equals("resources") || docs_list.getJSONObject(i).getString("id").equals("branding") || docs_list.getJSONObject(i).getString("id").startsWith("_design") || docs_list.getJSONObject(i).getJSONObject("doc").has("type") && (docs_list.getJSONObject(i).getJSONObject("doc").get("type").toString().equals("translations") || docs_list.getJSONObject(i).getJSONObject("doc").get("type").toString().equals("target"))) {
					Log.d("Translations: ", "found");
				}else{
					if(docs_list.getJSONObject(i).getJSONObject("doc").has("_attachments")){
						String contentType = docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").getString("content_type");
						String digest = docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").getString("digest");
						docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").remove("content_type");
						docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").remove("digest");
						contentType = "application/xml";
						digest= digest.replace("\\\\/","/");
						docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").put("content_type", contentType);
						docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").put("revpos", 1);
						docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").put("digest", digest);

						Log.d("application / content", docs_list.getJSONObject(i).getJSONObject("doc").getJSONObject("_attachments").getJSONObject("content").getString("content_type").toString());
					}
					Iterator<String> keys = docs_list.getJSONObject(i).getJSONObject("doc").keys();
					while(keys.hasNext()) {
						String key = keys.next();
						docs_list.getJSONObject(i).put(key,docs_list.getJSONObject(i).getJSONObject("doc").get(key));
					}
					docs_list.getJSONObject(i).remove("doc");

					if (docs_list.getJSONObject(i).has("id")){
						docs_list.getJSONObject(i).remove("id");
					}
					newDocs.put(docs_list.getJSONObject(i));
				}

			}
		} catch (JSONException e) {
			Log.d("Error json type", "json file");
			e.printStackTrace();
		}
		if (newDocs != null && newDocs.length() > 0 ){
			docs= "{\"docs\":"+newDocs.toString()+"}";
		}
		File file = null;
		DateFormat df = new SimpleDateFormat("yyyyMMddhhmmss");
		Log.d("storage", String.valueOf(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())));
		Log.d("version", String.valueOf(Build.VERSION.SDK_INT));
		File cht_data = new File(this.parent.getExternalFilesDir("Documents")+"/cht_data/");
		if (!cht_data.exists()){
			cht_data.mkdirs();
		}
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			file = new File(this.parent.getExternalFilesDir("Documents")+"/cht_data/", username+"_"+ LocalDateTime.now()
					.truncatedTo(ChronoUnit.SECONDS)
					.toString()
					.replace("-","")
					.replace(":","")+ ".txt");
		}
		else {
			file = new File(this.parent.getExternalFilesDir("Documents")+"/cht_data/", username+"_"+LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
					.toString()
					.replace("-","")
					.replace(":","")+ ".txt");
		}
		Log.d("file", file.toString());
		if(!file.getParentFile().exists()){
			Log.d("Creating parent", file.getParent().toString());
			file.getParentFile().mkdirs();
		}
		if(!file.exists()){
			try {
				file.createNewFile();
			}catch (Exception e){
				e.printStackTrace();
			}
		}
		Log.d("file exists", String.valueOf(file.exists()));
		try {
			FileWriter fileWriter = new FileWriter(file);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
			bufferedWriter.write(docs);
			bufferedWriter.close();
			toastResult("Download Completed");
		} catch (FileNotFoundException e){
			toastResult("Could not find the file");
			e.printStackTrace();
		} catch (IOException e) {
			toastResult("Could not write the file to storage");
			e.printStackTrace();
		}


	}
	@android.webkit.JavascriptInterface
	public void playAlert() {
		try {
			if(soundAlert != null) soundAlert.trigger();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public String getDataUsage() {
		try {
			int uid = Process.myUid();
			return new JSONObject()
					.put("system", getDataUsage(
							TrafficStats.getTotalRxBytes(),
							TrafficStats.getTotalTxBytes()))
					.put("app", getDataUsage(
							TrafficStats.getUidRxBytes(uid),
							TrafficStats.getUidTxBytes(uid)))
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching data usage stats.");
		}
	}

	private JSONObject getDataUsage(long rx, long tx) throws JSONException {
		return new JSONObject()
				.put("rx", rx)
				.put("tx", tx);
	}

	@android.webkit.JavascriptInterface
	public boolean getLocationPermissions() {
		return this.parent.getLocationPermissions();
	}

	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement) {
		try {
			datePicker(targetElement, Calendar.getInstance());
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement, String initialDate) {
		try {
			DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, UK);
			Calendar c = Calendar.getInstance();
			c.setTime(dateFormat.parse(initialDate));
			datePicker(targetElement, c);
		} catch(ParseException ex) {
			datePicker(targetElement);
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public boolean mrdt_available() {
		try {
			return mrdt.isAppInstalled();
		} catch(Exception ex) {
			logException(ex);
			return false;
		}
	}

	@android.webkit.JavascriptInterface
	public void mrdt_verify() {
		try {
			mrdt.startVerify();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@android.webkit.JavascriptInterface
	public boolean sms_available() {
		return smsSender != null;
	}

	/**
	 * @param id id associated with this message, e.g. a pouchdb docId
	 * @param destination the recipient phone number for this message
	 * @param content the text content of the SMS to be sent
	 */
	@android.webkit.JavascriptInterface
	public void sms_send(String id, String destination, String content) throws Exception {
		try {
			// TODO we may need to do this on a background thread to avoid the browser UI from blocking while the SMS is being sent.  Check.
			smsSender.send(new SmsSender.Sms(id, destination, content));
		} catch(Exception ex) {
			logException(ex);
			throw ex;
		}
	}

	@android.webkit.JavascriptInterface
	public void launchExternalApp(String action, String category, String type, String extras, String uri, String packageName, String flags) {
		try {
			JSONObject parsedExtras = extras == null ? null : new JSONObject(extras);
			Uri parsedUri = uri == null ? null : Uri.parse(uri);
			Integer parsedFlags = flags == null ? null : Integer.parseInt(flags);

			ChtExternalApp chtExternalApp = new ChtExternalApp
					.Builder()
					.setAction(action)
					.setCategory(category)
					.setType(type)
					.setExtras(parsedExtras)
					.setUri(parsedUri)
					.setPackageName(packageName)
					.setFlags(parsedFlags)
					.build();
			this.chtExternalAppHandler.startIntent(chtExternalApp);

		} catch (Exception ex) {
			logException(ex);
		}
	}

	@SuppressLint("ObsoleteSdkInt")
	@SuppressFBWarnings("REC_CATCH_EXCEPTION")
	@android.webkit.JavascriptInterface
	public String getDeviceInfo() {
		try {
			if (activityManager == null) {
				return jsonError("ActivityManager not set. Cannot retrieve RAM info.");
			}

			if (connectivityManager == null) {
				return jsonError("ConnectivityManager not set. Cannot retrieve network info.");
			}

			PackageInfo packageInfo = parent
					.getPackageManager()
					.getPackageInfo(parent.getPackageName(), 0);
			long versionCode = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ? (long)packageInfo.versionCode : packageInfo.getLongVersionCode();

			JSONObject appObject = new JSONObject();
			appObject.put("version", packageInfo.versionName);
			appObject.put("packageName", packageInfo.packageName);
			appObject.put("versionCode", versionCode);

			String androidVersion = Build.VERSION.RELEASE;
			int osApiLevel = Build.VERSION.SDK_INT;
			String osVersion = System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
			JSONObject softwareObject = new JSONObject();
			softwareObject
					.put("androidVersion", androidVersion)
					.put("osApiLevel", osApiLevel)
					.put("osVersion", osVersion);

			String device = Build.DEVICE;
			String model = Build.MODEL;
			String manufacturer = Build.BRAND;
			String hardware = Build.HARDWARE;
			Map<String, String> cpuInfo = getCPUInfo();
			JSONObject hardwareObject = new JSONObject();
			hardwareObject
					.put("device", device)
					.put("model", model)
					.put("manufacturer", manufacturer)
					.put("hardware", hardware)
					.put("cpuInfo", new JSONObject(cpuInfo));

			File dataDirectory = Environment.getDataDirectory();
			StatFs dataDirectoryStat = new StatFs(dataDirectory.getPath());
			long dataDirectoryBlockSize = dataDirectoryStat.getBlockSizeLong();
			long dataDirectoryAvailableBlocks = dataDirectoryStat.getAvailableBlocksLong();
			long dataDirectoryTotalBlocks = dataDirectoryStat.getBlockCountLong();
			long freeMemorySize = dataDirectoryAvailableBlocks * dataDirectoryBlockSize;
			long totalMemorySize = dataDirectoryTotalBlocks * dataDirectoryBlockSize;
			JSONObject storageObject = new JSONObject();
			storageObject
					.put("free", freeMemorySize)
					.put("total", totalMemorySize);

			MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
			activityManager.getMemoryInfo(memoryInfo);
			long totalRAMSize = memoryInfo.totalMem;
			long freeRAMSize = memoryInfo.availMem;
			long thresholdRAM = memoryInfo.threshold;
			JSONObject ramObject = new JSONObject();
			ramObject
					.put("free", freeRAMSize)
					.put("total", totalRAMSize)
					.put("threshold", thresholdRAM);

			NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
			JSONObject networkObject = new JSONObject();
			if (netInfo != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
				NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
				int downSpeed = networkCapabilities.getLinkDownstreamBandwidthKbps();
				int upSpeed = networkCapabilities.getLinkUpstreamBandwidthKbps();
				networkObject
						.put("downSpeed", downSpeed)
						.put("upSpeed", upSpeed);
			}

			return new JSONObject()
					.put("app", appObject)
					.put("software", softwareObject)
					.put("hardware", hardwareObject)
					.put("storage", storageObject)
					.put("ram", ramObject)
					.put("network", networkObject)
					.toString();
		} catch (Exception ex) {
			logException(ex);
			return jsonError("Problem fetching device info: ", ex);
		}
	}
	public JSONObject parseJSON(JSONObject json)
	{
		Iterator<String> iter = json.keys();
		while (iter.hasNext())
		{
			String key = iter.next();
			if (key.equals("-xmlns:i") ||
					key.equals("-i:nil") ||
					key.equals("-xmlns:d4p1") ||
					key.equals("-i:type") ||
					key.equals("#text")) // I want to delete items with those ID strings
			{
				json.remove(key);
			}
			//Object value = json.get(key);
		}
		return json;
	}
	//> PRIVATE HELPER METHODS
	private void datePicker(String targetElement, Calendar initialDate) {
		// Remove single-quotes from the `targetElement` CSS selecter, as
		// we'll be using these to enclose the entire string in JS.  We
		// are not trying to properly escape these characters, just prevent
		// suprises from JS injection.
		final String safeTargetElement = targetElement.replace('\'', '_');

		DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
			public void onDateSet(DatePicker view, int year, int month, int day) {
				++month;
				String dateString = String.format(UK, "%04d-%02d-%02d", year, month, day);
				String setJs = String.format("$('%s').val('%s').trigger('change')",
						safeTargetElement, dateString);
				parent.evaluateJavascript(setJs);
			}
		};

		// Make sure that the datepicker uses spinners instead of calendars.  Material design
		// does not support non-calendar view, so we explicitly use the Holo theme here.
		// Rumours suggest this may still show a calendar view on Android 24.  This has not been confirmed.
		// https://stackoverflow.com/questions/28740657/datepicker-dialog-without-calendar-visualization-in-lollipop-spinner-mode
		DatePickerDialog dialog = new DatePickerDialog(parent, android.R.style.Theme_Holo_Dialog, listener,
				initialDate.get(YEAR), initialDate.get(MONTH), initialDate.get(DAY_OF_MONTH));

		DatePicker picker = dialog.getDatePicker();
		picker.setCalendarViewShown(false);
		picker.setSpinnersShown(true);

		dialog.show();
	}

	private static HashMap getCPUInfo() throws IOException {
		try(
				Reader fileReader = new InputStreamReader(new FileInputStream("/proc/cpuinfo"), StandardCharsets.UTF_8);
				BufferedReader bufferedReader = new BufferedReader(fileReader);
		) {
			String line;
			HashMap output = new HashMap();
			while ((line = bufferedReader.readLine()) != null) {
				String[] data = line.split(":");
				if (data.length > 1) {
					String key = data[0].trim();
					if (key.equals("model name")) {
						output.put(key, data[1].trim());
						break;
					}
				}
			}

			int cores = Runtime.getRuntime().availableProcessors();
			output.put("cores", cores);

			String arch = System.getProperty("os.arch");
			output.put("arch", arch);

			return output;
		}
	}

	private void logException(Exception ex) {
		log(ex, "Exception thrown in JavascriptInterface function.");

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String stacktrace = sw.toString()
				.replace("\n", "; ")
				.replace("\t", " ");

		parent.errorToJsConsole("Exception thrown in JavascriptInterface function: %s", stacktrace);
	}

	//> STATIC HELPERS
	private static String jsonError(String message, Exception ex) {
		return jsonError(message + ex.getClass() + ": " + ex.getMessage());
	}

	private static String jsonError(String message) {
		return "{ \"error\": true, \"message\":\"" +
				jsonEscape(message) +
				"\" }";
	}

	private static String jsonEscape(String s) {
		return s.replaceAll("\"", "'");
	}
}
