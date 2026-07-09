package com.example.Japp.user.fragment.route;

import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RouteSampleData {

    private RouteSampleData() {}

    /** 南京鼓楼 → 夫子庙 示意折线 */
    public static List<LatLng> getMockPolyline() {
        return new ArrayList<>(Arrays.asList(
                new LatLng(32.060255, 118.796877),
                new LatLng(32.058500, 118.790200),
                new LatLng(32.052800, 118.783600),
                new LatLng(32.047900, 118.778900),
                new LatLng(32.043600, 118.775200),
                new LatLng(32.039800, 118.772800),
                new LatLng(32.023400, 118.792100)
        ));
    }
}
