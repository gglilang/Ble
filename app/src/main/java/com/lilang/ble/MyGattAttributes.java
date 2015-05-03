package com.lilang.ble;

import java.util.HashMap;

/**
 * Created by Lang on 2015/4/23.
 */
public class MyGattAttributes {
    private static HashMap<String, String> attributes = new HashMap<>();
    public static String WEIGHT = "527D54F8-C581-4C7E-BD1A-D0A3E28CF135";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        //体重秤的服务
        attributes.put("服务的UUID", "Get Weight Service");
        attributes.put("手机信息的服务", "Device Information Service");
        //体重秤的Characteristics
        attributes.put(WEIGHT, "Weight");
        attributes.put("制造商的名称", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName){
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
