package com.samourai.sentinel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
//import android.util.Log;

import com.samourai.sentinel.crypto.AESUtil;
import com.samourai.sentinel.segwit.SegwitAddress;
import com.samourai.sentinel.util.AddressFactory;
import com.samourai.sentinel.util.CharSequenceX;
import com.samourai.sentinel.util.MapUtil;
import com.samourai.sentinel.util.ReceiveLookAtUtil;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SamouraiSentinel {

    private static HashMap<String,String> xpubs = null;
    private static HashMap<String,String> legacy = null;
    private static HashMap<String,String> bip49 = null;
    private static HashMap<String,Integer> highestReceiveIdx = null;

    private static SamouraiSentinel instance = null;
    private static Context context = null;

    private static int currentSelectedAccount = 0;

    private static String dataDir = "wallet";
    private static String strFilename = "sentinel.dat";
    private static String strTmpFilename = "sentinel.bak";

    private static String strSentinelXPUB = "sentinel.xpub";
    private static String strSentinelBIP49 = "sentinel.bip49";
    private static String strSentinelLegacy = "sentinel.legacy";

    private SamouraiSentinel()    { ; }

    public static SamouraiSentinel getInstance(Context ctx)  {

        context = ctx;

        if(instance == null)    {
            xpubs = new HashMap<String,String>();
            bip49 = new HashMap<String,String>();
            legacy = new HashMap<String,String>();
            highestReceiveIdx = new HashMap<String,Integer>();

            instance = new SamouraiSentinel();
        }

        return instance;
    }

    public void setCurrentSelectedAccount(int account) {
        currentSelectedAccount = account;
    }

    public int getCurrentSelectedAccount() {
        return currentSelectedAccount;
    }

    public HashMap<String,String> getXPUBs()    { return xpubs; }

    public HashMap<String,String> getBIP49()    { return bip49; }

    public HashMap<String,String> getLegacy()    { return legacy; }

    public List<String> getAllAddrsSorted()    {

        HashMap<String,String> mapAll = new HashMap<String,String>();
        mapAll.putAll(xpubs);
        mapAll.putAll(bip49);
        mapAll = MapUtil.getInstance().sortByValue(mapAll);
        mapAll.putAll(MapUtil.getInstance().sortByValue(legacy));

        List<String> xpubList = new ArrayList<String>();
        xpubList.addAll(mapAll.keySet());

        return xpubList;
    }

    public HashMap<String,String> getAllMapSorted()    {

        HashMap<String,String> mapAll = new HashMap<String,String>();
        mapAll.putAll(xpubs);
        mapAll.putAll(bip49);
        mapAll = MapUtil.getInstance().sortByValue(mapAll);
        mapAll.putAll(MapUtil.getInstance().sortByValue(legacy));

        return mapAll;
    }

    public void parseJSON(JSONObject obj) {

        xpubs.clear();

        try {
            if(obj != null && obj.has("xpubs"))    {
                JSONArray _xpubs = obj.getJSONArray("xpubs");
                for(int i = 0; i < _xpubs.length(); i++)   {
                    JSONObject _obj = _xpubs.getJSONObject(i);
                    Iterator it = _obj.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        if(key.equals("receiveIdx"))    {
                            highestReceiveIdx.put(key, _obj.getInt(key));
                        }
                        else    {
                            xpubs.put(key, _obj.getString(key));
                        }
                    }
                }
            }

            if(obj != null && obj.has("bip49"))    {
                JSONArray _bip49s = obj.getJSONArray("bip49");
                for(int i = 0; i < _bip49s.length(); i++)   {
                    JSONObject _obj = _bip49s.getJSONObject(i);
                    Iterator it = _obj.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        if(key.equals("receiveIdx"))    {
                            highestReceiveIdx.put(key, _obj.getInt(key));
                        }
                        else    {
                            bip49.put(key, _obj.getString(key));
                        }
                    }
                }
            }

            if(obj != null && obj.has("legacy"))    {
                JSONArray _addr = obj.getJSONArray("legacy");
                for(int i = 0; i < _addr.length(); i++)   {
                    JSONObject _obj = _addr.getJSONObject(i);
                    Iterator it = _obj.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        legacy.put(key, _obj.getString(key));
                    }
                }
            }

            if(obj != null && obj.has("receives"))    {
                ReceiveLookAtUtil.getInstance().fromJSON(obj.getJSONArray("receives"));
            }

        }
        catch(JSONException ex) {
            throw new RuntimeException(ex);
        }

    }

    public JSONObject toJSON() {
        try {
            JSONObject obj = new JSONObject();

            JSONArray _xpubs = new JSONArray();
            for(String xpub : xpubs.keySet()) {
                JSONObject _obj = new JSONObject();
                _obj.put(xpub, xpubs.get(xpub));
                _obj.put("receiveIdx", highestReceiveIdx.get(xpub) == null ? 0 : highestReceiveIdx.get(xpub));
                _xpubs.put(_obj);
            }
            obj.put("xpubs", _xpubs);

            JSONArray _bip49s = new JSONArray();
            for(String b49 : bip49.keySet()) {
                JSONObject _obj = new JSONObject();
                _obj.put(b49, bip49.get(b49));
                _obj.put("receiveIdx", highestReceiveIdx.get(b49) == null ? 0 : highestReceiveIdx.get(b49));
                _bip49s.put(_obj);
            }
            obj.put("bip49", _bip49s);

            JSONArray _addr = new JSONArray();
            for(String addr : legacy.keySet()) {
                JSONObject _obj = new JSONObject();
                _obj.put(addr, legacy.get(addr));
                _addr.put(_obj);
            }
            obj.put("legacy", _addr);

            obj.put("receives", ReceiveLookAtUtil.getInstance().toJSON());

            return obj;
        }
        catch(JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean payloadExists()  {
        File dir = context.getDir(dataDir, Context.MODE_PRIVATE);
        File file = new File(dir, strFilename);
        return file.exists();
    }

    public synchronized void serialize(JSONObject jsonobj, CharSequenceX password) throws IOException, JSONException {

        File dir = context.getDir(dataDir, Context.MODE_PRIVATE);
        File newfile = new File(dir, strFilename);
        File tmpfile = new File(dir, strTmpFilename);

        // serialize to byte array.
        String jsonstr = jsonobj.toString(4);

        // prepare tmp file.
        if(tmpfile.exists()) {
            tmpfile.delete();
        }

        String data = null;
        if(password != null) {
            data = AESUtil.encrypt(jsonstr, password, AESUtil.DefaultPBKDF2Iterations);
        }
        else {
            data = jsonstr;
        }

        FileOutputStream fos = new FileOutputStream(tmpfile);
        fos.write(data.getBytes());
        fos.close();

        // rename tmp file
        if(tmpfile.renameTo(newfile)) {
//            mLogger.info("file saved to  " + newfile.getPath());
//            Log.i("HD_WalletFactory", "file saved to  " + newfile.getPath());
        }
        else {
//            mLogger.warn("rename to " + newfile.getPath() + " failed");
//            Log.i("HD_WalletFactory", "rename to " + newfile.getPath() + " failed");
        }

        saveToPrefs();

    }

    public synchronized JSONObject deserialize(CharSequenceX password) throws IOException, JSONException {

        File dir = context.getDir(dataDir, Context.MODE_PRIVATE);
        File file = new File(dir, strFilename);
        StringBuilder sb = new StringBuilder("");

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int n = 0;
        while((n = fis.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, n));
        }
        fis.close();

        JSONObject node = null;
        if(password == null) {
            node = new JSONObject(sb.toString());
        }
        else {
            String decrypted = null;
            try {
                decrypted = AESUtil.decrypt(sb.toString(), password, AESUtil.DefaultPBKDF2Iterations);
            }
            catch(Exception e) {
                return null;
            }
            if(decrypted == null) {
                return null;
            }
            node = new JSONObject(decrypted);
        }

        return node;
    }

    public void saveToPrefs()  {

        SharedPreferences _xpub = context.getSharedPreferences(strSentinelXPUB, 0);
        SharedPreferences.Editor xEditor = _xpub.edit();
        for(String xpub : xpubs.keySet()) {
            xEditor.putString(xpub, xpubs.get(xpub));
        }
        xEditor.commit();

        SharedPreferences _bip49 = context.getSharedPreferences(strSentinelBIP49, 0);
        SharedPreferences.Editor bEditor = _bip49.edit();
        for(String b49 : bip49.keySet()) {
            bEditor.putString(b49, bip49.get(b49));
        }
        bEditor.commit();

        SharedPreferences _legacy = context.getSharedPreferences(strSentinelLegacy, 0);
        SharedPreferences.Editor lEditor = _legacy.edit();
        for(String leg : legacy.keySet()) {
            lEditor.putString(leg, legacy.get(leg));
        }
        lEditor.commit();

    }

    public void restoreFromPrefs()  {

        SharedPreferences xpub = context.getSharedPreferences(strSentinelXPUB, 0);
        if(xpub != null)    {
            Map<String, ?> allXPUB = xpub.getAll();
            for (Map.Entry<String, ?> entry : allXPUB.entrySet()) {
                SamouraiSentinel.getInstance(context).getXPUBs().put(entry.getKey(), entry.getValue().toString());
            }
        }

        SharedPreferences bip49s = context.getSharedPreferences(strSentinelBIP49, 0);
        if(bip49s != null)    {
            Map<String, ?> all49 = bip49s.getAll();
            for (Map.Entry<String, ?> entry : all49.entrySet()) {
                SamouraiSentinel.getInstance(context).getBIP49().put(entry.getKey(), entry.getValue().toString());
            }
        }

        SharedPreferences legacy = context.getSharedPreferences(strSentinelLegacy, 0);
        if(legacy != null)    {
            Map<String, ?> allLegacy = legacy.getAll();
            for (Map.Entry<String, ?> entry : allLegacy.entrySet()) {
                SamouraiSentinel.getInstance(context).getLegacy().put(entry.getKey(), entry.getValue().toString());
            }
        }

    }

    public String getReceiveAddress()  {

        final List<String> xpubList = getAllAddrsSorted();

        String addr = null;
        ECKey ecKey = null;

        if(xpubList.get(SamouraiSentinel.getInstance(context).getCurrentSelectedAccount() - 1).startsWith("xpub"))    {
            String xpub = xpubList.get(SamouraiSentinel.getInstance(context).getCurrentSelectedAccount() - 1);
            Log.d("SamouraiSentinel", "xpub:" + xpub);
            int account = AddressFactory.getInstance(context).xpub2account().get(xpub);
            Log.d("SamouraiSentinel", "account:" + account);
            if(SamouraiSentinel.getInstance(context).getBIP49().keySet().contains(xpub))    {
                ecKey = AddressFactory.getInstance(context).getECKey(AddressFactory.RECEIVE_CHAIN, account);
                SegwitAddress segwitAddress = new SegwitAddress(ecKey.getPubKey(), MainNetParams.get());
                addr = segwitAddress.getAddressAsString();
                Log.d("SamouraiSentinel", "addr:" + addr);
            }
            else    {
                addr = AddressFactory.getInstance(context).get(AddressFactory.RECEIVE_CHAIN, account);
            }
        }
        else    {
            addr = xpubList.get(SamouraiSentinel.getInstance(context).getCurrentSelectedAccount() - 1);
        }

        return addr;

    }

}
