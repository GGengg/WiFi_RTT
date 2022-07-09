package com.example.rtttest1;

import java.util.ArrayList;
import java.util.List;

public class Configuration {

    public static final int Milliseconds_Before_Next_Ranging_Request = 0;

    public enum Testing_sites {
        Utility_Room_208,
        Mechanical_Building
    }

    AccessPoint AP1,AP2,AP3,AP4,AP5,AP6,AP7,AP8;
    ArrayList<AccessPoint> accessPoints;
    ArrayList<String> macAddresses;

    public Configuration(Testing_sites testing_sites){
        if (testing_sites == Testing_sites.Utility_Room_208){
            AP1 = new AccessPoint("b0:e4:d5:39:26:89",35.45,14.07);
            AP2 = new AccessPoint("cc:f4:11:8b:29:4d",49,15.11);
            AP3 = new AccessPoint("b0:e4:d5:01:26:f5",27.69,14.72);
            AP4 = new AccessPoint("b0:e4:d5:91:ba:5d",15.68,13.17);
            AP5 = new AccessPoint("b0:e4:d5:96:3b:95",12.08,5.6);
            AP6 = new AccessPoint("f8:1a:2b:06:3c:0b",29.1,5.6);
            AP7 = new AccessPoint("14:22:3b:2a:86:f5",39.31,5.6);
            AP8 = new AccessPoint("14:22:3b:16:5a:bd",50.43,5.6);

            accessPoints = new ArrayList<>();
            macAddresses = new ArrayList<>();
            accessPoints.add(AP1);
            accessPoints.add(AP2);
            accessPoints.add(AP3);
            accessPoints.add(AP4);
            accessPoints.add(AP5);
            accessPoints.add(AP6);
            accessPoints.add(AP7);
            accessPoints.add(AP8);

            macAddresses.add(AP1.getBSSID());
            macAddresses.add(AP2.getBSSID());
            macAddresses.add(AP3.getBSSID());
            macAddresses.add(AP4.getBSSID());
            macAddresses.add(AP5.getBSSID());
            macAddresses.add(AP6.getBSSID());
            macAddresses.add(AP7.getBSSID());
            macAddresses.add(AP8.getBSSID());
        }
        else if (testing_sites == Testing_sites.Mechanical_Building){
            AP1 = new AccessPoint("b0:e4:d5:39:26:89",10.3,21.67);
            AP2 = new AccessPoint("cc:f4:11:8b:29:4d",0.4,20);
            AP3 = new AccessPoint("b0:e4:d5:01:26:f5",17.25,14.53);
            AP4 = new AccessPoint("b0:e4:d5:91:ba:5d",17.4,20.6);
            AP5 = new AccessPoint("b0:e4:d5:96:3b:95",0.4,9.5);
            AP6 = new AccessPoint("f8:1a:2b:06:3c:0b",0.4,16.24);
            AP7 = new AccessPoint("14:22:3b:2a:86:f5",25.34,18.5);
            AP8 = new AccessPoint("14:22:3b:16:5a:bd",10.28,12.4);

            accessPoints = new ArrayList<>();
            macAddresses = new ArrayList<>();
            accessPoints.add(AP1);
            accessPoints.add(AP2);
            accessPoints.add(AP3);
            accessPoints.add(AP4);
            accessPoints.add(AP5);
            accessPoints.add(AP6);
            accessPoints.add(AP7);
            accessPoints.add(AP8);

            macAddresses.add(AP1.getBSSID());
            macAddresses.add(AP2.getBSSID());
            macAddresses.add(AP3.getBSSID());
            macAddresses.add(AP4.getBSSID());
            macAddresses.add(AP5.getBSSID());
            macAddresses.add(AP6.getBSSID());
            macAddresses.add(AP7.getBSSID());
            macAddresses.add(AP8.getBSSID());
        }
    }

    public List<AccessPoint> getConfiguration(){
        return accessPoints;
    }

    public List<String> getMacAddresses(){
        return macAddresses;
    }
}
