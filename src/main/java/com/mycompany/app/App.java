/**
 * Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mycompany.app;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.data.ArcGISFeatureTable;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.MobileMapPackage;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import java.util.concurrent.ExecutionException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class App extends Application {

    private MapView mapView;

    public static void main(String[] args) {

        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {

        // set the title and size of the stage and show it
        stage.setTitle("My Map App");
        stage.setWidth(800);
        stage.setHeight(700);
        stage.show();

        // create a JavaFX scene with a stack pane as the root node and add it to the scene
        StackPane stackPane = new StackPane();
        Scene scene = new Scene(stackPane);
        stage.setScene(scene);

        ArcGISMap map = new ArcGISMap(SpatialReferences.getWgs84());

        // create a MapView to display the map and add it to the stack pane
        mapView = new MapView();
        mapView.setMap(map);
        stackPane.getChildren().add(mapView);

        GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        mapView.getGraphicsOverlays().add(graphicsOverlay);
        SimpleLineSymbol simpleLineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 10);

        MobileMapPackage mobileMapPackage = new MobileMapPackage("ResponderTracks.mmpk");
        mobileMapPackage.loadAsync();
        mobileMapPackage.addDoneLoadingListener(() -> {
            System.out.println("loaded " + mobileMapPackage.getLoadStatus());


            FeatureLayer layer = (FeatureLayer) mobileMapPackage.getMaps().get(0).getOperationalLayers().get(0);
            System.out.println("name : " + layer.getName());

            ArcGISFeatureTable table = (ArcGISFeatureTable) layer.getFeatureTable();

            QueryParameters queryParameters = new QueryParameters();
            queryParameters.setWhereClause("1=1");

            var resultFuture = table.queryFeaturesAsync(queryParameters);
            resultFuture.addDoneListener(()-> {
                System.out.println("got result");
                try {
                    var result = resultFuture.get();

                    for (Feature feature : result) {
                        System.out.println("adding graphic");
                        Graphic graphic = new Graphic(feature.getGeometry(), feature.getAttributes(), simpleLineSymbol);
                        graphicsOverlay.getGraphics().add(graphic);

                    }

                    generateJson(graphicsOverlay);



                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });


        });
    }

    private void generateJson(GraphicsOverlay graphicsOverlay) {
        boolean done = false;
        boolean gotData;
        int position = 0;

        while (!done) {

            gotData = false;

            for (Graphic graphic : graphicsOverlay.getGraphics()) {
                Polyline line = (Polyline) graphic.getGeometry();

                String id = (String) graphic.getAttributes().get("ID");
                String trackType = (String) graphic.getAttributes().get("TrackType");


                try {
                    Point point = line.getParts().get(0).getPoint(position);
                    Point latLong = (Point) GeometryEngine.project(point, SpatialReferences.getWgs84());

                    //calculate bearing if not the first point
                    double heading = 0;
                    if (position > 1) {
                        Point lastPoint = line.getParts().get(0).getPoint(position-1);
                        heading = headingBetweenPoints(point, lastPoint);
                    }

                    double altitude = 0;
                    double speed = 0;

                    // set altitude and speed for helicopter
                    if (id.equals("WS61-4")) {
                        altitude = 300;
                        speed = 20;
                    }

                    // {"geometry":{"x":-122.39063,"y":47.62897,"spatialReference":{"wkid":4326}},"attributes":{"ID":"Responder01","Heading":45.4,"Altitude":0,"Speed":0,"CallSign":"VEH001","TrackType":"VEHICLE"}}

                    System.out.println("{\"geometry\":{\"x\":" + latLong.getX() + ",\"y\":" + latLong.getY() + ",\"spatialReference\":{\"wkid\":4326}}," +
                        "\"attributes\":{\"ID\":\"" + id + "\"," +
                        "\"Heading\":" + heading + "," +
                        "\"Altitude\":" + altitude + "," +
                        "\"Speed\":" + speed + "," +
                        "\"CallSign\":\"" + id + "\"," +
                        "\"TrackType\":\"" + trackType + "\"}}"
                    );
                    gotData = true;
                } catch (Exception e) {};

                position++;
                if (!gotData) done = true;
            }
        }
    }

    private double headingBetweenPoints(Point point, Point originPoint) {

        //double heading = 0;

        var xDiff = point.getX() - originPoint.getX();
        var yDiff = point.getY() - originPoint.getY();

        //var distance = GeometryEngine.distanceBetween(originPoint,point);

        var bearing = Math.toDegrees(Math.atan(xDiff / yDiff));
        // correct if heading South West
        if (yDiff < 0 && xDiff < 0) {
            bearing = bearing -180;
        }

        // correct if heading South East
        if (yDiff < 0 && xDiff > 0) {
            bearing = 180 + bearing;
        }
        return bearing;
    }

    /**
     * Stops and releases all resources used in application.
     */
    @Override
    public void stop() {

        if (mapView != null) {
            mapView.dispose();
        }
    }
}
