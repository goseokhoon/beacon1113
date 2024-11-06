package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
class KalmanFilter {
    private double q; // 프로세스 노이즈 공분산
    private double r; // 측정 노이즈 공분산
    private double x; // 상태 (위치)
    private double p; // 추정 오차 공분산
    private double k; // 칼만 이득

    public KalmanFilter(double q, double r) {
        this.q = q;
        this.r = r;
        this.x = 0; // 초기 상태
        this.p = 1; // 초기 오차 공분산
    }

    public double update(double measurement) {
        // 예측 단계
        p += q;

        // 업데이트 단계
        k = p / (p + r);
        x += k * (measurement - x);
        p *= (1 - k);
        return x;
    }
}

class Particle {
    double x, y, weight;

    public Particle(double x, double y, double weight) {
        this.x = x;
        this.y = y;
        this.weight = weight;
    }
}

class ParticleFilter {
    private List<Particle> particles;
    private int numParticles;
    private Random random;
    private double width, height;

    public ParticleFilter(int numParticles, double width, double height) {
        this.numParticles = numParticles;
        this.width = width;
        this.height = height;
        this.random = new Random();
        initializeParticles();
    }

    private void initializeParticles() {
        particles = new ArrayList<>(numParticles);
        for (int i = 0; i < numParticles; i++) {
            particles.add(new Particle(random.nextDouble() * width, random.nextDouble() * height, 1.0 / numParticles));
        }
    }

    public double[] update(Map<Integer, Double> beaconDistances) {
        // 입자 이동 및 위치 업데이트
        for (Particle p : particles) {
            p.x += random.nextGaussian() * 0.3;
            p.y += random.nextGaussian() * 0.3;
            p.x = Math.max(0, Math.min(p.x, width));
            p.y = Math.max(0, Math.min(p.y, height));
        }

        // 가중치 계산
        double totalWeight = 0;
        for (Particle p : particles) {
            double likelihood = 1.0;
            for (Map.Entry<Integer, Double> entry : beaconDistances.entrySet()) {
                int beaconId = entry.getKey();
                double measuredDistance = entry.getValue();
                double[] beaconPos = getBeaconPosition(beaconId);
                double calculatedDistance = Math.sqrt(Math.pow(p.x - beaconPos[0], 2) + Math.pow(p.y - beaconPos[1], 2));
                likelihood *= Math.exp(-Math.abs(measuredDistance - calculatedDistance));
            }
            p.weight *= likelihood;
            totalWeight += p.weight;
        }

        // 가중치 정규화 및 리샘플링
        for (Particle p : particles) {
            p.weight /= totalWeight;
        }

        // 리샘플링
        List<Particle> newParticles = new ArrayList<>(numParticles);
        for (int i = 0; i < numParticles; i++) {
            double r = random.nextDouble();
            double sum = 0;
            for (Particle p : particles) {
                sum += p.weight;
                if (sum >= r) {
                    newParticles.add(new Particle(p.x, p.y, 1.0 / numParticles));
                    break;
                }
            }
        }
        particles = newParticles;

        // 평균 위치 계산
        double sumX = 0, sumY = 0;
        for (Particle p : particles) {
            sumX += p.x;
            sumY += p.y;
        }
        return new double[]{sumX / numParticles, sumY / numParticles};
    }

    private double[] getBeaconPosition(int beaconId) {
        switch (beaconId) {
            case 1:
                return new double[]{1.5, 2.5};
            case 2:
                return new double[]{2.5, 0.5};
            case 3:
                return new double[]{3.5, 2.5};
            default:
                return new double[]{0, 0};
        }
    }
}

class ExponentialMovingAverage {
    private double alpha;
    private double oldValue;
    private boolean initialized;

    public ExponentialMovingAverage(double alpha) {
        this.alpha = alpha;
        this.initialized = false;
    }

    public double addSample(double sample) {
        if (!initialized) {
            oldValue = sample;
            initialized = true;
        }
        oldValue = alpha * sample + (1 - alpha) * oldValue;
        return oldValue;
    }
}

class MedianFilter {
    private LinkedList<Double> window = new LinkedList<>();
    private int size;

    public MedianFilter(int size) {
        this.size = size;
    }

    public double addSample(double sample) {
        window.addLast(sample);
        if (window.size() > size) {
            window.removeFirst();
        }
        LinkedList<Double> sortedWindow = new LinkedList<>(window);
        Collections.sort(sortedWindow);
        return sortedWindow.get(sortedWindow.size() / 2);
    }
}

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "Beacontest";
    private BeaconManager beaconManager;
    private List<Beacon> beaconList = new ArrayList<>();
    private CustomView customView;
    private Map<String, ExponentialMovingAverage> rssiAverages = new HashMap<>();
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int TARGET_MAJOR_VALUE = 10011;
    private static final double A = -70; // RSSI 상수
    private static final double N = 2.5; // 거리 감쇠 지수
    public static final double MART_WIDTH = 5.0;
    public static final double MART_HEIGHT = 3.0;
    private static final double MAX_DISTANCE = 3.0; // 최대 허용 거리
    private KalmanFilter kalmanFilterX;
    private KalmanFilter kalmanFilterY;
    private Map<String, MedianFilter> rssiMedianFilters = new HashMap<>();
    private static final int MEDIAN_FILTER_SIZE = 2;
    private static final double POSITION_UPDATE_THRESHOLD = 0.1; // meters
    private double[] lastPosition = null;
    private ParticleFilter particleFilter;
    private Map<String, Double> rssiConstantA = new HashMap<>();
    private Map<String, Double> pathLossExponentN1 = new HashMap<>();
    private Map<String, Double> pathLossExponentN2 = new HashMap<>();
    private Map<String, Double> rssiCalibration = new HashMap<>();
    private Map<Integer, Long> beaconStayTimes = new HashMap<>();
    private static final long RECOMMENDATION_DELAY = 5000; // 5초
    public static boolean recommendationShown = false; // 추천 다이얼로그가 이미 표시되었는지 확인하는 변수
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // 각 비콘에 대해 다른 RSSI 상수 및 감쇠 지수 설정
        rssiConstantA.put("beacon1", -65.0);
        rssiConstantA.put("beacon2", -65.0);
        rssiConstantA.put("beacon3", -65.0);

        pathLossExponentN1.put("beacon1", 3.5);
        pathLossExponentN1.put("beacon2", 3.5);
        pathLossExponentN1.put("beacon3", 3.5);

        pathLossExponentN2.put("beacon1", 3.5);
        pathLossExponentN2.put("beacon2", 3.5);
        pathLossExponentN2.put("beacon3", 3.5);

        // 칼만 필터 초기화 - 파라미터 최적화
        kalmanFilterX = new KalmanFilter(0.02, 0.15);
        kalmanFilterY = new KalmanFilter(0.02, 0.15);

        particleFilter = new ParticleFilter(1000, MART_WIDTH, MART_HEIGHT);
        beaconManager = BeaconManager.getInstanceForApplication(this);
        customView = findViewById(R.id.custom_view);

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier((beacons, region) -> {
            Log.d(TAG, "비콘 탐지 중: " + beacons.size() + "개 비콘 발견");
            if (beacons.size() > 0) {
                beaconList.clear();
                beaconList.addAll(beacons);
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting ranging", e);
        }
    }


    private static final double N1 = 2.0; // Path loss exponent for LOS
    private static final double N2 = 3.3; // Path loss exponent for NLOS
    private static final double X_C = 3.0; // Breakpoint distance

    private double calculateDistance(double rssi, String beaconId) {
        // RSSI 보정 적용
        double calibratedRssi = rssi + rssiCalibration.getOrDefault(beaconId, 0.0);

        // 비콘별 RSSI 상수 및 감쇠 지수 가져오기
        double A = rssiConstantA.getOrDefault(beaconId, -70.0);
        double N1 = pathLossExponentN1.getOrDefault(beaconId, 2.5);
        double N2 = pathLossExponentN2.getOrDefault(beaconId, 3.3);

        double distance;
        // 비콘 간 상호 검증을 통해 신호가 약해진 경우 보정
        if (allBeaconsWeak()) {
            Log.d(TAG, "모든 비콘의 신호가 동시에 약해짐. 간섭 가능성 탐지.");
            calibratedRssi += 2.0; // 신호 보정 강화
        }

        // LOS (Line-of-Sight)와 NLOS (Non-Line-of-Sight) 조건에 따른 거리 계산
        if (calibratedRssi >= A - 10 * N1 * Math.log10(X_C)) {
            // LOS condition
            distance = Math.pow(10, (A - calibratedRssi) / (10 * N1));
        } else {
            // NLOS condition
            distance = X_C * Math.pow(10, (A - calibratedRssi - 10 * N1 * Math.log10(X_C)) / (10 * N2));
        }

        // 너무 짧은 거리나 너무 먼 거리의 경우 보정
        double MIN_DISTANCE = 0.1; // 최소 허용 거리
        distance = Math.max(distance, MIN_DISTANCE);
        distance = Math.min(distance, MAX_DISTANCE); // 최대 허용 거리 적용

        // 동적 RSSI 보정
        updateRssiCalibration(beaconId, distance);

        return distance;
    }


    private boolean allBeaconsWeak() {
        int weakCount = 0;
        for (Beacon beacon : beaconList) {
            if (beacon.getRssi() < -80) {
                weakCount++;
            }
        }
        // 3개 중 2개 이상이 약한 경우만 간섭으로 간주
        return weakCount >= 2;
    }

    private void updateRssiCalibration(String beaconId, double calculatedDistance) {
        double knownDistance = getKnownDistance(beaconId);
        double error = knownDistance - calculatedDistance;

        double currentCalibration = rssiCalibration.getOrDefault(beaconId, 0.0);
        double newCalibration = currentCalibration + error * 0.03;

        // 보정값의 범위를 제한하여 모든 비콘의 신호가 지나치게 약해지지 않도록 함
        newCalibration = Math.max(-5.0, Math.min(newCalibration, 5.0));

        rssiCalibration.put(beaconId, newCalibration);
    }

    private double getKnownDistance(String beaconId) {
        // 비콘의 알려진 위치와 현재 추정된 사용자 위치 사이의 거리 계산
        double[] beaconPosition;
        switch (beaconId) {
            case "beacon1":
                beaconPosition = new double[]{1.5, 2.5};
                break;
            case "beacon2":
                beaconPosition = new double[]{2.5, 0.5};
                break;
            case "beacon3":
                beaconPosition = new double[]{3.5, 2.5};
                break;
            default:
                return MAX_DISTANCE; // 알 수 없는 비콘의 경우 최대 거리 반환
        }

        // 마지막으로 알려진 사용자 위치 사용
        double[] userPosition = lastPosition != null ? lastPosition : new double[]{MART_WIDTH /2, MART_HEIGHT / 2}; // 중앙을 기본 위치로 설정합니다.
        // 유클리드 거리 계산
        return Math.sqrt(Math.pow(beaconPosition[0] - userPosition[0], 2) + Math.pow(beaconPosition[1] - userPosition[1], 2));
    }

    private double calculateDistance(double[] p1, double[] p2) {
        return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StringBuilder sb = new StringBuilder();
            Map<Integer, Double> beaconDistances = new HashMap<>();

            for (Beacon beacon : beaconList) {
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                String address = beacon.getBluetoothAddress();

                if (major == TARGET_MAJOR_VALUE && (minor == 1 || minor == 2 || minor == 3)) {
                    rssiMedianFilters.putIfAbsent(address, new MedianFilter(MEDIAN_FILTER_SIZE));
                    rssiAverages.putIfAbsent(address, new ExponentialMovingAverage(0.3));

                    double filteredRssi = rssiMedianFilters.get(address).addSample(beacon.getRssi());
                    double smoothedRssi = rssiAverages.get(address).addSample(filteredRssi);
                    double distance = calculateDistance(smoothedRssi, "beacon" + minor);
                    distance = Math.min(distance, MAX_DISTANCE);
                    if (distance < 0.1) {
                        distance = 0.1;
                    }

                    beaconDistances.put(minor, distance);

                    sb.append("비콘 ").append(minor).append(": ")
                            .append(String.format("%.2f", distance)).append("m\n");

                    double beaconX, beaconY;
                    int color;

                    switch (minor) {
                        case 1:
                            beaconX = 1;
                            beaconY = 2.8;
                            color = Color.RED;
                            break;
                        case 2:
                            beaconX = 2.5;
                            beaconY = 0.3;
                            color = Color.YELLOW;
                            break;
                        case 3:
                            beaconX = 4;
                            beaconY = 2.8;
                            color = Color.GREEN;
                            break;
                        default:
                            continue;
                    }

                    float beaconXScreen = (float) (beaconX / MART_WIDTH * customView.getWidth());
                    float beaconYScreen = (float) (beaconY / MART_HEIGHT * customView.getHeight());
                    float radius = (float) (distance / Math.max(MART_WIDTH, MART_HEIGHT) * Math.max(customView.getWidth(), customView.getHeight()));

                    customView.updateBeaconPosition(minor - 1, beaconXScreen, beaconYScreen, radius, color);

                    // 비콘 거리와 시간 조건 체크
                    if (distance <= 0.5 && !recommendationShown) { // 0.5m 이내에 있을 때
                        long currentTime = System.currentTimeMillis();
                        if (!beaconStayTimes.containsKey(minor)) {
                            beaconStayTimes.put(minor, currentTime); // 처음으로 비콘이 감지되었을 때 시간 저장
                        } else {
                            long elapsedTime = currentTime - beaconStayTimes.get(minor);
                            if (elapsedTime >= RECOMMENDATION_DELAY) { // 5초가 지나고 다이얼로그가 아직 표시되지 않았다면
                                navigateToRecommendationActivity(minor); // 추천 액티비티 호출
                                recommendationShown = true; // 다이얼로그 표시 상태 업데이트
                            }
                        }
                    } else {
                        // 비콘이 감지되지 않으면 상태를 리셋
                        beaconStayTimes.remove(minor);
                    }
                }
            }

            if (beaconDistances.size() == 3) {
                double[] userPosition = calculateUserPosition(beaconDistances);
                if (userPosition != null) {
                    userPosition[0] = kalmanFilterX.update(userPosition[0]);
                    userPosition[1] = kalmanFilterY.update(userPosition[1]);

                    if (lastPosition == null || calculateDistance(lastPosition, userPosition) > POSITION_UPDATE_THRESHOLD) {
                        if (lastPosition != null) {
                            double distanceMoved = calculateDistance(lastPosition, userPosition);
                            double maxAllowedDistance = 0.1;
                            double alpha = Math.min(1.0, maxAllowedDistance / distanceMoved);

                            userPosition[0] = lastPosition[0] + (userPosition[0] - lastPosition[0]) * alpha;
                            userPosition[1] = lastPosition[1] + (userPosition[1] - lastPosition[1]) * alpha;
                        }

                        userPosition[0] = Math.max(0, Math.min(userPosition[0], MART_WIDTH));
                        userPosition[1] = Math.max(0, Math.min(userPosition[1], MART_HEIGHT));

                        lastPosition = userPosition;
                        float userXScreen = (float) (userPosition[0] / MART_WIDTH * customView.getWidth());
                        float userYScreen = (float) ((MART_HEIGHT - userPosition[1]) / MART_HEIGHT * customView.getHeight());

                        userXScreen = Math.max(0, Math.min(userXScreen, customView.getWidth()));
                        userYScreen = Math.max(0, Math.min(userYScreen, customView.getHeight()));

                        customView.setUserPosition(userXScreen, userYScreen);

                        sb.append("사용자 위치: (")
                                .append(String.format("%.2f", userPosition[0])).append(", ")
                                .append(String.format("%.2f", userPosition[1])).append(")\n");

                        for (Map.Entry<Integer, Double> entry : beaconDistances.entrySet()) {
                            updateRssiCalibration("beacon" + entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            TextView textView = findViewById(R.id.TextView);
            if (textView != null) {
                textView.setText(sb.toString());
            }

            customView.invalidate();
            sendEmptyMessageDelayed(0, 250); // 갱신 간격
        }
    };


    private void navigateToRecommendationActivity(int beaconId) {
        Intent intent = new Intent(this, RecommendationActivity.class);
        intent.putExtra("beaconId", beaconId);
        startActivity(intent);

        // 다이얼로그가 표시되면 상태 업데이트
        recommendationShown = true;
    }
    // RecommendationActivity에서 다이얼로그 닫기 버튼 클릭 시
    private void onCloseRecommendationDialog() {
        // 다이얼로그를 닫을 때 호출되는 메서드
        recommendationShown = false; // 다이얼로그가 닫혔으므로 다시 표시 가능
        finish(); // Activity 종료
    }


    private boolean allBeaconsStrong() {
        for (Beacon beacon : beaconList) {
            if (beacon.getRssi() < -65) { // 기존 -70에서 -65로 신호 강도 기준 상향
                return false;
            }
        }
        return true;
    }

    private double[] calculateUserPosition(Map<Integer, Double> distances) {
        double[][] beacons = {{1.5, 2.5}, {2.5, 0.5}, {3.5, 2.5}};

        // 삼변측량법을 사용한 위치 추정
        double[] trilaterationPosition = trilaterate(distances);

        // 파티클 필터 적용
        double[] particleFilterPosition = particleFilter.update(distances);

        double trilaterationWeight, particleFilterWeight;
        if (allBeaconsStrong()) {
            trilaterationWeight = 0.95; // 신호가 강할 때 삼변측량의 가중치를 높임
            particleFilterWeight = 0.05;
        } else {
            trilaterationWeight = 0.7; // 신호가 불안정할 때는 파티클 필터 가중치를 높임
            particleFilterWeight = 0.3;
        }

        double userX = trilaterationPosition[0] * trilaterationWeight + particleFilterPosition[0] * particleFilterWeight;
        double userY = trilaterationPosition[1] * trilaterationWeight + particleFilterPosition[1] * particleFilterWeight;

        // 거리 기반 가중치 적용
        if (distances.size() == 3) {
            double totalX = 0;
            double totalY = 0;
            double totalWeight = 0;

            boolean isCloseToOneBeacon = false;
            int closestBeaconId = -1;

            // 0.5m 이하로 가까운 비콘 탐지
            for (Map.Entry<Integer, Double> entry : distances.entrySet()) {
                if (entry.getValue() <= 0.5) {
                    isCloseToOneBeacon = true;
                    closestBeaconId = entry.getKey();
                    break;
                }
            }

            for (Map.Entry<Integer, Double> entry : distances.entrySet()) {
                int minor = entry.getKey();
                double distance = entry.getValue();
                double[] beaconPos = beacons[minor - 1];
                double weight;

                if (isCloseToOneBeacon && minor != closestBeaconId) {
                    // 특정 비콘에 너무 가까운 경우 나머지 비콘의 거리를 최소 1.5m 이상으로 보정
                    distance = Math.max(distance, 1.5);
                    weight = 0.1; // 멀리 있는 비콘들에 낮은 가중치 적용
                } else {
                    // 일반적인 거리 기반 가중치
                    weight = 1 / Math.max(distance, 0.1);
                }

                totalX += beaconPos[0] * weight;
                totalY += beaconPos[1] * weight;
                totalWeight += weight;
            }

            // 세 개의 비콘이 비슷한 거리에 있을 경우 중앙으로 치우치게 보정
            if (Math.abs(distances.get(1) - distances.get(2)) < 0.3 &&
                    Math.abs(distances.get(2) - distances.get(3)) < 0.3 &&
                    Math.abs(distances.get(3) - distances.get(1)) < 0.3) {
                double centralWeight = 1.0; // 중앙으로 치우치게 하기 위한 가중치
                totalX += (MART_WIDTH / 2) * centralWeight;
                totalY += (MART_HEIGHT / 2) * centralWeight;
                totalWeight += centralWeight;
            }

            if (totalWeight > 0) {
                userX = (userX + totalX / totalWeight) / 2;
                userY = (userY + totalY / totalWeight) / 2;
            }
        }

        // 최종 위치를 마트 경계 내로 제한
        userX = Math.max(0, Math.min(userX, MART_WIDTH));
        userY = Math.max(0, Math.min(userY, MART_HEIGHT));

        // 칼만 필터 적용
        userX = kalmanFilterX.update(userX);
        userY = kalmanFilterY.update(userY);

        return new double[]{userX, userY};
    }

    private double[] trilaterate(Map<Integer, Double> distances) {
        double[][] beacons = {{1.5, 2.5}, {2.5, 0.5}, {3.5, 2.5}};

        // 삼변측량 계산
        double[] centroid = {0, 0};
        double totalWeight = 0;

        for (int i = 0; i < 3; i++) {
            // 각 비콘의 거리에 대해 가중치를 설정
            double distance = distances.get(i + 1);
            if (distance < MAX_DISTANCE) {
                double weight = 1.0 / Math.pow(distance, 2); // 거리에 대한 가중치 (역제곱)

                centroid[0] += beacons[i][0] * weight;
                centroid[1] += beacons[i][1] * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight > 0) {
            centroid[0] /= totalWeight;
            centroid[1] /= totalWeight;
        } else {
            // 교점이 없는 경우 비콘 위치의 평균을 사용
            centroid[0] = MART_WIDTH / 2; // 중앙 X 좌표
            centroid[1] = MART_HEIGHT / 2; // 중앙 Y 좌표
        }

        return centroid;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Functionality limited")
                        .setMessage("Since location access has not been granted, this app will not be able to discover beacons.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MapActivity resumed, binding beacon manager");

        // 상태를 초기화
        recommendationShown = false; // 다이얼로그 상태 리셋
        beaconList.clear(); // 비콘 리스트 초기화

        // 비콘 매니저가 바인딩되지 않았다면 바인딩
        if (!beaconManager.isBound(this)) {
            beaconManager.bind(this);
        }

        // 비콘 탐지 시작
        handler.sendEmptyMessage(0); // 비콘 탐지 시작
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "MapActivity paused, unbinding beacon manager");
        super.onPause();
        if (beaconManager.isBound(this)) {
            beaconManager.unbind(this);
        }
    }
}