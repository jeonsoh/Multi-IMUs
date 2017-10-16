package nisargpatel.deadreckoning.graph;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;

public class ScatterPlot {

    private String seriesName;
    private ArrayList<Double> x_CurrentList;
    private ArrayList<Double> y_CurrentList;
    private ArrayList<Double> x_ReceivedList;
    private ArrayList<Double> y_ReceivedList;

    public ScatterPlot (String seriesName) {
        this.seriesName = seriesName;
        x_CurrentList = new ArrayList<>();
        y_CurrentList = new ArrayList<>();
        x_ReceivedList = new ArrayList<>();
        y_ReceivedList = new ArrayList<>();
    }

    public GraphicalView getGraphView(Context context) {


        XYMultipleSeriesDataset myMultiSeries;
        XYMultipleSeriesRenderer myMultiRenderer;

        //adding the x-axis data from an ArrayList to a standard array
        double[] xSet = new double[x_CurrentList.size()];
        for (int i = 0; i < x_CurrentList.size(); i++)
            xSet[i] = x_CurrentList.get(i);

        //adding the y-axis data from an ArrayList to a standard array
        double[] ySet = new double[y_CurrentList.size()];
        for (int i = 0; i < y_CurrentList.size(); i++)
            ySet[i] = y_CurrentList.get(i);

        //받은 자표---------
        double[] x_ReceivedSet = new double[x_ReceivedList.size()];
        for (int i = 0; i < x_ReceivedList.size(); i++)
            x_ReceivedSet[i] = x_ReceivedList.get(i);

        //adding the y-axis data from an ArrayList to a standard array
        double[] y_ReceivedSet = new double[y_ReceivedList.size()];
        for (int i = 0; i < y_ReceivedList.size(); i++)
            y_ReceivedSet[i] = y_ReceivedList.get(i);
        //-----------------

        //creating a new sequence using the x-axis and y-axis data

        XYSeries mySeries = new XYSeries(seriesName);
        for (int i = 0; i < xSet.length; i++)
            mySeries.add(xSet[i], ySet[i]);

        //defining chart visual properties
        XYSeriesRenderer myRenderer = new XYSeriesRenderer();
        myRenderer.setFillPoints(true);
        myRenderer.setPointStyle(PointStyle.CIRCLE);
        myRenderer.setColor(Color.GREEN);

        myMultiSeries = new XYMultipleSeriesDataset();
        myMultiSeries.addSeries(mySeries);

        myMultiRenderer = new XYMultipleSeriesRenderer();
        myMultiRenderer.addSeriesRenderer(myRenderer);


        XYSeriesRenderer received_myRenderer = new XYSeriesRenderer();
        received_myRenderer.setFillPoints(true);
        received_myRenderer.setPointStyle(PointStyle.CIRCLE);
        received_myRenderer.setColor(Color.RED);

        XYSeries received_mySeries = new XYSeries(seriesName);
        for (int i = 0; i < x_ReceivedSet.length; i++)
            received_mySeries.add(x_ReceivedSet[i], y_ReceivedSet[i]);
        myMultiSeries.addSeries(received_mySeries);
        myMultiRenderer.addSeriesRenderer(received_myRenderer);


        //setting text graph element sizes
        myMultiRenderer.setPointSize(5); //size of scatter plot points
        myMultiRenderer.setShowLegend(false); //hide legend

        //set chart and label sizes
        myMultiRenderer.setChartTitle("Position");
        myMultiRenderer.setChartTitleTextSize(75);
        myMultiRenderer.setLabelsTextSize(40);

        //setting X labels and Y labels position
        int[] chartMargins = {100, 100, 25, 100}; //top, left, bottom, right
        myMultiRenderer.setMargins(chartMargins);
        myMultiRenderer.setYLabelsPadding(50);
        myMultiRenderer.setXLabelsPadding(10);

        //setting chart min/max
        //double bound = getMaxBound();
        /*myMultiRenderer.setXAxisMin(-400);
        myMultiRenderer.setXAxisMax(400);
        myMultiRenderer.setYAxisMin(-400);
        myMultiRenderer.setYAxisMax(400);
        myMultiRenderer.setZoomEnabled(false,false);
        myMultiRenderer.setPanEnabled(false,false);*/
        return ChartFactory.getScatterChartView(context, myMultiSeries, myMultiRenderer);
    }

    //add a point to the series
    public void addPoint(double x, double y) {
        x_CurrentList.add(x);
        y_CurrentList.add(y);
        /*
        if(x > 300){
            if(y > 300){
                yList.add(300.0);
                xList.add(300.0);
            }else{
                yList.add(y);
                xList.add(300.0);
            }
        }else{
            if(y > 300){
                yList.add(300.0);
                xList.add(x);
            }else{
                yList.add(y);
                xList.add(x);
            }
        }*/
        Log.e("x,y","x: "+x+", y : "+y);
    }

    public void add_ReceivedPoint(double x, double y) {
        x_ReceivedList.add(x);
        y_ReceivedList.add(y);
        /*
        if(x > 300){
            if(y > 300){
                yList.add(300.0);
                xList.add(300.0);
            }else{
                yList.add(y);
                xList.add(300.0);
            }
        }else{
            if(y > 300){
                yList.add(300.0);
                xList.add(x);
            }else{
                yList.add(y);
                xList.add(x);
            }
        }*/
        Log.e("x,y","x: "+x+", y : "+y);
    }

    public float getLastXPoint() {
        double x = x_CurrentList.get(x_CurrentList.size() - 1);
        return (float)x;

    }

    public float getLastYPoint() {
        double y = y_CurrentList.get(y_CurrentList.size() - 1);
        return (float)y;
    }

    public void clearSet() {
        x_CurrentList.clear();
        y_CurrentList.clear();
        x_ReceivedList.clear();
        y_ReceivedList.clear();
    }

    private double getMaxBound() {
        double max = 0;
        for (double num : x_CurrentList)
            if (max < Math.abs(num))
                max = num;
        for (double num : y_CurrentList)
            if (max < Math.abs(num))
                max = num;
        return (Math.abs(max) / 100) * 100 + 100; //rounding up to the nearest hundred
    }
}
