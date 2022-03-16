/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runon.trading.view;

import com.seomse.commons.utils.FileUtil;
import com.seomse.commons.utils.time.DateUtil;
import io.runon.trading.PriceOpenTime;
import io.runon.trading.technical.analysis.candle.CandleStick;
import io.runon.trading.technical.analysis.candle.TradeCandle;
import io.runon.trading.view.util.BrowserUtil;
import io.runon.trading.view.util.JarUtil;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 트레이딩 차트
 * @author ccsweets
 */
public class TradingChart {

    /* 차트 데이터 타입 */
    public enum ChartDateType {MINUTE, DAY}

    /* 캔들 데이터 */
    CandleStick[] candleStickArr;
    /* HTML chart create String */
    StringBuilder createChartStr = new StringBuilder();
    /* 차트 날짜유형 */
    ChartDateType dateType;

    /* pureJs contents */
    String pureJsContents;
    /* LightWeight Js contents */
    String lightWeightJsContents;

    /* title */
    String browserTitle = "runon LightWeight-Chart View";

    /* html file export path */
    String exportPath = "data";

    static final String LEFT_LINE_REPLACER = "$$LEFT_LINE_REPLACER$$";
    boolean leftLineEnabled = false;


    /**
     * 브라우저 타이틀을 설정 한다.
     * @param  browserTitle browserTitle
     */
    public void setBrowserTitle(String browserTitle) {
        this.browserTitle = browserTitle;
    }

    /**
     * Constructor
     * @param candleStickArr 캔들스틱 배열
     */
    public TradingChart(CandleStick[] candleStickArr){
        this(candleStickArr,600,300, ChartDateType.DAY);
    }
    /**
     * Constructor
     * @param candleStickArr 캔들스틱 배열
     * @param width X축 길이
     * @param height Y축 높이
     * @param dateType 날짜유형
     */
    public TradingChart(CandleStick[] candleStickArr , int width , int height , ChartDateType dateType){


       pureJsContents = JarUtil.readFromJarFile("pure.js");
       lightWeightJsContents = JarUtil.readFromJarFile("lightweight-charts.standalone.production.js");

        this.candleStickArr = candleStickArr;
        this.dateType = dateType;
        createChartStr.append( """
                var chart = LightweightCharts.createChart(document.body, {
                    width: %d,
                  height: %d,
                  %s
                  crosshair: {
                    mode: LightweightCharts.CrosshairMode.Normal,
                  }
                });
                                
                const candlestickSeries = chart.addCandlestickSeries({
                  priceScaleId: 'right'
                });

                                
                """.formatted(width,height,LEFT_LINE_REPLACER));


        if(dateType == ChartDateType.MINUTE){
            createChartStr.append( """
                chart.applyOptions({
                        timeScale: {
                            // Adds hours and minutes to the chart.
                            timeVisible: true,
                            secondsVisible: false
                        }
                    });
                """);
        }


        createChartStr.append("candlestickSeries.setData([");
        int candleStickArrSize = candleStickArr.length;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < candleStickArrSize; i++) {
            CandleStick candleStick = candleStickArr[i];
            String timeStr = Long.toString(candleStick.getOpenTime()/1000);
            createChartStr.append("""
                    {
                        close: %s,
                        high: %s,
                        low: %s,
                        open: %s,
                        time: %s
                      },
                    """.formatted(
                    candleStick.getClose().stripTrailingZeros().toString()
                    ,candleStick.getHigh().stripTrailingZeros().toString()
                    ,candleStick.getLow().stripTrailingZeros().toString()
                    ,candleStick.getOpen().stripTrailingZeros().toString()
                    ,timeStr
            ));
        }
        createChartStr.setLength(createChartStr.length()-1);
        createChartStr.append("]);\n");
    }



    public void addVolume(TradeCandle[] candles){

        VolumeData[] volumeDataArr = new VolumeData[candles.length];

        for (int i = 0; i < candles.length ; i++) {
            TradeCandle candle = candles[i];

            VolumeData volumeData = new VolumeData();
            volumeData.volume = candles[i].getVolume();
            volumeData.time = candles[i].getOpenTime();
            if(candle.getChange().compareTo(BigDecimal.ZERO) >= 0){
                volumeData.color = "#26a69a";
            }else{
                volumeData.color = "red";
            }

            volumeDataArr[i] = volumeData;
        }

        addVolume(volumeDataArr, new BigDecimal("0.85"), BigDecimal.ZERO);
    }

    /**
     * 거래량 데이터를 전부 추가한다.
     * @param volumeDataArr 거래량 데이터 배열
     * @param topMargin topMargin
     * @param bottomMargin bottomMargin
     */
    public void addVolume(VolumeData[] volumeDataArr, BigDecimal topMargin , BigDecimal bottomMargin){
        createChartStr.append("""
                var volumeSeries = chart.addHistogramSeries({
                  	color: '#26a69a',
                  	priceFormat: {
                  		type: 'volume',
                  	},
                  	priceScaleId: '',
                  	scaleMargins: {
                  		top: %s,
                  		bottom: %s,
                  	},
                  });
                  volumeSeries.setData([
                """.formatted(topMargin.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                , bottomMargin.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()));

        int volumeDataArrSize = volumeDataArr.length;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < volumeDataArrSize; i++) {
            VolumeData volumeData = volumeDataArr[i];
            String timeStr = Long.toString(volumeData.getTime()/1000);

            createChartStr.append("""
                { time: %s, value: %s, color: '%s' },
                """.formatted(timeStr, volumeData.getVolume().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() , volumeData.getColor()));
        }
        createChartStr.append("]);");
    }

    /**
     * 선형 데이터를 전부 추가한다.
     * @param lineDataArr 선형 데이터 배열
     * @param color 색깔
     * @param size 굵기
     */
    public void addLine(PriceOpenTime[] lineDataArr, String color, int size){
        addLine(lineDataArr, color, size, true);
    }

    /**
     * 선형 데이터를 전부 추가한다.
     * @param lineDataArr 선형 데이터 배열
     * @param color 색깔
     * @param size 굵기
     * @param rightSide 라인을 오른쪽에 표시할지 여부
     */
    public void addLine(PriceOpenTime[] lineDataArr , String color, int size, boolean rightSide){
        createChartStr.append("""
                chart.addLineSeries({
                  color: '%s',
                  lineWidth: %d,
                  priceScaleId: '%s',
                  priceLineVisible: false,
                  lastValueVisible: false
                }).setData([
                """.formatted(color,size, rightSide? "right": "left"));
        if(!rightSide){
            createChartStr = new StringBuilder(createChartStr.toString().replace(LEFT_LINE_REPLACER, "leftPriceScale: {visible: true, },"));
        }
        int lineDataArrSize = lineDataArr.length;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < lineDataArrSize; i++) {
            PriceOpenTime lineData = lineDataArr[i];
//            long openTime = lineData.getTime();
            BigDecimal price = lineData.getClose();

            String timeStr = Long.toString(lineData.getOpenTime()/1000);
            createChartStr.append("""
                    {
                        time: %s,
                        value: %s
                      },
                    """.formatted(
                    timeStr,price.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            ));

        }

        createChartStr.setLength(createChartStr.length()-1);
        createChartStr.append("]);\n");
    }

    /**
     * HTML 데이터를 전달 받는다.
     * @return HTML
     */
    public String getHtml(){
        //noinspection StringBufferReplaceableByString
        StringBuilder result = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <head>
                  <title>%s</title>
                  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
                </head>
                <body>
                    <script>%s</script>
                    <script>%s</script>
                  
                """.formatted(browserTitle,pureJsContents,lightWeightJsContents)
        );

        result.append("<script>\n").append(createChartStr.toString().replace(LEFT_LINE_REPLACER,"")).append("\n</script>\n");

        return result.append("</body></html>").toString();
    }




    private List<MarkerData> markerDataList = null;

    /**
     * 차트에 마커를 전부 추가 한다.
     * @param markerDataArray 마커 데이터 배열
     */
    public void addMarker(MarkerData[] markerDataArray) {

        if(markerDataList == null){
            markerDataList = new ArrayList<>();
        }
        Collections.addAll(markerDataList, markerDataArray);
    }

    public void addMarker(MarkerData markerData) {
        if(markerDataList == null){
            markerDataList = new ArrayList<>();
        }
        markerDataList.add(markerData);
    }

    public void addMarker(List<MarkerData> list) {
        if(markerDataList == null){
            markerDataList = new ArrayList<>();
        }
        markerDataList.addAll(list);
    }

    public void setMarker(){
        if(markerDataList == null || markerDataList.size() == 0){
            return;
        }

        MarkerData[] array = markerDataList.toArray(new MarkerData[0]);
        Arrays.sort(array, MarkerData.SORT_TIME);

        createChartStr.append("""
              var markers = [];
                """);

        for (MarkerData markerData : array) {
            String timeStr = Long.toString(markerData.getTime()/1000);
            createChartStr.append("""
                markers.push({ time: %s, position: '%s', color: '%s', shape: '%s', text: '%s'});
                """
                    .formatted(timeStr,markerData.getMarkerType().name(),markerData.getColor(),markerData.getMarkerShape().name(),markerData.getText())
            );
        }
        createChartStr.append("candlestickSeries.setMarkers(markers);");

        markerDataList.clear();
    }

    /**
     * 결과를 HTML 파일로 생성 한다.
     * @return html file path
     */
    public String makeHtmlFile(){
        return makeHtmlFile(DateUtil.getDateYmd(System.currentTimeMillis(),"yyyyMMddHHmmss") + ".html");
    }

    /**
     * 결과를 HTML 파일로 생성 한다.
     * @param exportFileName out file name
     * @return html file path
     */
    public String makeHtmlFile(String exportFileName){
        setMarker();

        File exportDir = new File(exportPath);
        if(!exportDir.exists()){
            //noinspection ResultOfMethodCallIgnored
            exportDir.mkdir();
        }
        String exportFileFullPath = exportPath + "/" + exportFileName;
        FileUtil.fileOutput(getHtml(),exportFileFullPath , false);
        return exportFileFullPath;
    }

    /**
     * 데이터를 브라우저로 확인 한다.
     */
    public void view(){
        String exportFileFullPath = makeHtmlFile();
        File viewHtmlFile = new File(exportFileFullPath);
        String htmlFileAbsolutePath = viewHtmlFile.getAbsolutePath();
        BrowserUtil.loadChromeByFile(htmlFileAbsolutePath);
    }
}
