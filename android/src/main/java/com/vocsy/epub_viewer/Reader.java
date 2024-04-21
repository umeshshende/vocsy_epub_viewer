package com.vocsy.epub_viewer;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folioreader.Config;
import com.folioreader.FolioReader;
import com.folioreader.model.HighLight;
import com.folioreader.model.locators.ReadLocator;
import com.folioreader.ui.base.OnSaveHighlight;
import com.folioreader.util.OnHighlightListener;
import com.folioreader.util.ReadLocatorListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;

public class Reader implements OnHighlightListener, ReadLocatorListener, FolioReader.OnClosedListener {

    private ReaderConfig readerConfig;
    public FolioReader folioReader;
    private Context context;
    public MethodChannel.Result result;
    private EventChannel eventChannel;
    private EventChannel.EventSink pageEventSink;
    private BinaryMessenger messenger;
    private ReadLocator read_locator;
    private static final String PAGE_CHANNEL = "sage";
    private String pathToBeChanged="";
    private File file =null;
    Reader(Context context, BinaryMessenger messenger, ReaderConfig config, EventChannel.EventSink sink) {
        this.context = context;
        readerConfig = config;

        getHighlightsAndSave();
        //setPageHandler(messenger);

        folioReader = FolioReader.get()
                .setOnHighlightListener(this)
                .setReadLocatorListener(this)
                .setOnClosedListener(this);
        pageEventSink = sink;
    }
    private static final String ALGORITHM = "AES";
    private static final String KEY = "234b4eec-b3b2-49f2-9dbe-23c7da855d83";

    public static void encryptFile(File inputFile, File outputFile) throws Exception {
        SecretKeySpec secretKey = generateKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        FileInputStream inputStream = new FileInputStream(inputFile);
        byte[] inputBytes = new byte[(int) inputFile.length()];
        inputStream.read(inputBytes);

        byte[] outputBytes = cipher.doFinal(inputBytes);

        FileOutputStream outputStream = new FileOutputStream(outputFile);
        outputStream.write(outputBytes);

        inputStream.close();
        outputStream.close();
    }

    public static void decryptFile(File encryptedFile, File decryptedFile) throws Exception {
        SecretKeySpec secretKey = generateKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        FileInputStream inputStream = new FileInputStream(encryptedFile);
        byte[] inputBytes = new byte[(int) encryptedFile.length()];
        inputStream.read(inputBytes);

        byte[] outputBytes = cipher.doFinal(inputBytes);

        FileOutputStream outputStream = new FileOutputStream(decryptedFile);
        outputStream.write(outputBytes);

        inputStream.close();
        outputStream.close();
    }

    private static SecretKeySpec generateKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(KEY.getBytes("UTF-8"));
        return new SecretKeySpec(key, ALGORITHM);
    }
    public void open(String bookPath, String lastLocation) {
        final String path = bookPath;
        final String location = lastLocation;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Log.i("SavedLocation", "-> savedLocation -> " + location);
                    if (location != null && !location.isEmpty()) {
                        ReadLocator readLocator = ReadLocator.fromJson(location);
                        folioReader.setReadLocator(readLocator);
                    }
                    Log.i("path", "path"+path);
                    file = new File(path);
                    String guid = UUID.randomUUID().toString();
                    String encryptedFilePath = path.replace(file.getName(), file.getName().replace(".epub","")+"_" + "encr" + ".aes");
                    File file2 = new File(encryptedFilePath);

                    // encrypted file present
                    if(file2.exists()){
                        // descrypt file
                        decryptFile(file2,file);
                    }else{
                        encryptFile(file,file2);
                    }
                    folioReader.setConfig(readerConfig.config, true)
                            .openBook(file.getPath());

                  //  encryptFile(file,file2);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public void close() {

        folioReader.close();
    }

    private void setPageHandler(BinaryMessenger messenger) {
//        final MethodChannel channel = new MethodChannel(registrar.messenger(), "page");
//        channel.setMethodCallHandler(new EpubKittyPlugin());
        Log.i("event sink is", "in set page handler:");
        eventChannel = new EventChannel(messenger, PAGE_CHANNEL);

        try {

            eventChannel.setStreamHandler(new EventChannel.StreamHandler() {

                @Override
                public void onListen(Object o, EventChannel.EventSink eventSink) {

                    Log.i("event sink is", "this is eveent sink:");

                    pageEventSink = eventSink;
                    if (pageEventSink == null) {
                        Log.i("empty", "Sink is empty");
                    }
                }

                @Override
                public void onCancel(Object o) {

                }
            });
        } catch (Error err) {
            Log.i("and error", "error is " + err.toString());
        }
    }

    private void getHighlightsAndSave() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<HighLight> highlightList = null;
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    highlightList = objectMapper.readValue(
                            loadAssetTextAsString("highlights/highlights_data.json"),
                            new TypeReference<List<HighlightData>>() {
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (highlightList == null) {
                    folioReader.saveReceivedHighLights(highlightList, new OnSaveHighlight() {
                        @Override
                        public void onFinished() {
                            //You can do anything on successful saving highlight list
                        }
                    });
                }
            }
        }).start();
    }


    private String loadAssetTextAsString(String name) {
        BufferedReader in = null;
        try {
            StringBuilder buf = new StringBuilder();
            InputStream is = context.getAssets().open(name);
            in = new BufferedReader(new InputStreamReader(is));

            String str;
            boolean isFirst = true;
            while ((str = in.readLine()) != null) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append('\n');
                buf.append(str);
            }
            return buf.toString();
        } catch (IOException e) {
            Log.e("Reader", "Error opening asset " + name);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e("Reader", "Error closing asset " + name);
                }
            }
        }
        return null;
    }

    @Override
    public void onFolioReaderClosed() {
        if(file!=null && file.exists()){
            file.delete();
        }
      //  renamedFile.delete();
        Log.i("readLocator", "-> saveReadLocator -> " + read_locator.toJson());

        if (pageEventSink != null) {
            pageEventSink.success(read_locator.toJson());
        }
    }

    @Override
    public void onHighlight(HighLight highlight, HighLight.HighLightAction type) {

    }

    @Override
    public void saveReadLocator(ReadLocator readLocator) {
        read_locator = readLocator;
    }

}
