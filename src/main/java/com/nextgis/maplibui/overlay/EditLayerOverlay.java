/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import com.cocosw.undobar.UndoBarController;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.GeoEnvelope;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoMultiLineString;
import com.nextgis.maplib.datasource.GeoMultiPoint;
import com.nextgis.maplib.datasource.GeoMultiPolygon;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.VectorCacheItem;
import com.nextgis.maplibui.BottomToolbar;
import com.nextgis.maplibui.MapViewOverlays;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.EditEventListener;
import com.nextgis.maplibui.api.MapViewEventListener;
import com.nextgis.maplibui.api.Overlay;
import com.nextgis.maplibui.util.ConstantsUI;

import java.util.ArrayList;
import java.util.List;


/**
 * The class for edit vector features
 */
public class EditLayerOverlay
        extends Overlay implements MapViewEventListener
{

    public final static int MODE_NONE      = 0;
    public final static int MODE_HIGHLIGHT = 1;
    public final static int MODE_EDIT      = 2;
    public final static int MODE_CHANGE    = 3;

    protected final static int VERTEX_RADIUS = 20;
    protected final static int EDGE_RADIUS   = 12;
    protected final static int LINE_WIDTH    = 4;

    public final static long DELAY      = 750;

    protected VectorLayer     mLayer;
    protected VectorCacheItem mItem;
    protected int             mMode;
    protected Paint           mPaint;
    protected int             mFillColor;
    protected int             mOutlineColor;
    protected int             mSelectColor;
    protected Bitmap          mAnchor;
    protected float           mAnchorRectOffsetX, mAnchorRectOffsetY;
    protected float mAnchorCenterX, mAnchorCenterY;
    protected DrawItems mDrawItems;

    protected List<EditEventListener> mListeners;

    protected static final int mType = 3;

    protected static final String BUNDLE_KEY_MODE    = "mode";
    protected static final String BUNDLE_KEY_LAYER   = "layer";
    protected static final String BUNDLE_KEY_FEATURE = "feature";

    protected float mTolerancePX;
    protected float mAnchorTolerancePX;

    protected GeoGeometry mChangedGeometry;
    protected PointF mTempPointOffset;
    protected boolean mHasEdits;
    protected BottomToolbar mCurrentToolbar;

    public EditLayerOverlay(
            Context context,
            MapViewOverlays mapViewOverlays)
    {
        super(context, mapViewOverlays);
        mLayer = null;
        mItem = null;
        mMode = MODE_NONE;


        mTolerancePX = context.getResources().getDisplayMetrics().density * ConstantsUI.TOLERANCE_DP;

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        mFillColor = mContext.getResources().getColor(R.color.accent);
        mOutlineColor = Color.BLACK;
        mSelectColor = Color.RED;

        mAnchor =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_action_anchor);
        mAnchorRectOffsetX = -mAnchor.getWidth() * 0.1f;
        mAnchorRectOffsetY = -mAnchor.getHeight() * 0.1f;
        mAnchorCenterX = mAnchor.getWidth() * 0.75f;
        mAnchorCenterY = mAnchor.getHeight() * 0.75f;
        mAnchorTolerancePX = mAnchor.getScaledWidth(context.getResources().getDisplayMetrics());

        mDrawItems = new DrawItems();

        mListeners = new ArrayList<>();

        mMapViewOverlays.addListener(this);
        mHasEdits = false;
    }


    public void setMode(int mode)
    {
        mMode = mode;
        if (mMode == MODE_EDIT) {
            for (EditEventListener listener : mListeners) {
                listener.onStartEditSession();
            }
            mDrawItems.setSelectedPoint(0);
            mDrawItems.setSelectedRing(0);
            mMapViewOverlays.postInvalidate();
        } else if (mMode == MODE_NONE) {
            if(mHasEdits){
                //TODO: Ask to save
                mHasEdits = false;
            }
            mLayer = null;
            mItem = null;
            mDrawItems.setSelectedPoint(Constants.NOT_FOUND);
            mDrawItems.setSelectedRing(Constants.NOT_FOUND);
            mMapViewOverlays.postInvalidate();
        } else if (mMode == MODE_HIGHLIGHT) {
            mMapViewOverlays.postInvalidate();
        }
    }


    public void setFeature(
            VectorLayer layer,
            VectorCacheItem item)
    {
        mLayer = layer;
        mItem = item;

        setMode(MODE_HIGHLIGHT);
    }


    @Override
    public void draw(
            Canvas canvas,
            MapDrawable mapDrawable)
    {
        if (null == mItem)
            return;
        GeoGeometry geom = mItem.getGeoGeometry();
        if (null == geom)
            return;

        if(mHasEdits && null != mChangedGeometry)
            fillDrawItems(mChangedGeometry, mapDrawable);
        else
            fillDrawItems(geom, mapDrawable);

        switch (geom.getType()) {
            case GeoConstants.GTPoint:
            case GeoConstants.GTMultiPoint:
                mDrawItems.drawPoints(canvas);
                break;
            case GeoConstants.GTLineString:
            case GeoConstants.GTMultiLineString:
            case GeoConstants.GTPolygon:
            case GeoConstants.GTMultiPolygon:
                mDrawItems.drawLines(canvas);
                break;
            default:
                break;
        }
    }


    protected void fillDrawItems(
            GeoGeometry geom,
            MapDrawable mapDrawable)
    {
        mDrawItems.clear();

        GeoPoint[] geoPoints = null;
        float[] points = null;
        GeoLineString lineString = null;
        switch (geom.getType()) {
            case GeoConstants.GTPoint:
                geoPoints = new GeoPoint[1];
                geoPoints[0] = (GeoPoint) geom;
                points = mapDrawable.mapToScreen(geoPoints);
                mDrawItems.addItems(0, points, DrawItems.TYPE_VERTEX);
                break;
            case GeoConstants.GTMultiPoint:
                GeoMultiPoint geoMultiPoint = (GeoMultiPoint) geom;
                geoPoints = new GeoPoint[geoMultiPoint.size()];
                for (int i = 0; i < geoMultiPoint.size(); i++) {
                    geoPoints[i] = geoMultiPoint.get(i);
                }
                points = mapDrawable.mapToScreen(geoPoints);
                mDrawItems.addItems(0, points, DrawItems.TYPE_VERTEX);
                break;
            case GeoConstants.GTLineString:
                lineString = (GeoLineString)geom;
                fillDrawLine(0, lineString, mapDrawable);
                break;
            case GeoConstants.GTMultiLineString:
                GeoMultiLineString multiLineString = (GeoMultiLineString)geom;
                for(int i = 0; i < multiLineString.size(); i++){
                    fillDrawLine(i, multiLineString.get(i), mapDrawable);
                }
                break;
            case GeoConstants.GTPolygon:
                GeoPolygon polygon = (GeoPolygon)geom;
                fillDrawPolygon(polygon, mapDrawable);
                break;
            case GeoConstants.GTMultiPolygon:
                GeoMultiPolygon multiPolygon = (GeoMultiPolygon)geom;
                for(int i = 0; i < multiPolygon.size(); i++){
                    fillDrawPolygon(multiPolygon.get(i), mapDrawable);
                }
                break;
            case GeoConstants.GTGeometryCollection:
            default:
                break;
        }
    }

    protected void fillDrawPolygon(GeoPolygon polygon, MapDrawable mapDrawable){
        fillDrawLine(0, polygon.getOuterRing(), mapDrawable);
        for(int i = 0; i < polygon.getInnerRingCount(); i++){
            fillDrawLine(i + 1, polygon.getInnerRing(i), mapDrawable);
        }
    }

    protected void fillDrawLine(int ring, GeoLineString lineString, MapDrawable mapDrawable){
        GeoPoint[] geoPoints = lineString.getPoints().toArray(new GeoPoint[lineString.getPoints().size()]);
        float[] points = mapDrawable.mapToScreen(geoPoints);
        mDrawItems.addItems(ring, points, DrawItems.TYPE_VERTEX);
        float[] edgePoints = new float[points.length - 1];
        for(int i = 0; i < points.length - 1; i++){
            edgePoints[i] = (points[i] + points[i + 1]) * .5f;
        }
        mDrawItems.addItems(ring, edgePoints, DrawItems.TYPE_EDGE);
    }

    @Override
    public void drawOnPanning(
            Canvas canvas,
            PointF currentMouseOffset)
    {
        if(null == mItem)
            return;
        GeoGeometry geom = mItem.getGeoGeometry();
        if(null == geom)
            return;

        DrawItems drawItems = mDrawItems;
        if(mMode !=MODE_CHANGE) {
            drawItems = mDrawItems.pan(currentMouseOffset);
        }

        switch (geom.getType()) {
            case GeoConstants.GTPoint:
            case GeoConstants.GTMultiPoint:
                drawItems.drawPoints(canvas);
                break;
            case GeoConstants.GTLineString:
            case GeoConstants.GTMultiLineString:
            case GeoConstants.GTPolygon:
            case GeoConstants.GTMultiPolygon:
                drawItems.drawLines(canvas);
                break;
            default:
                break;
        }
    }


    @Override
    public void drawOnZooming(
            Canvas canvas,
            PointF currentFocusLocation,
            float scale)
    {
        if(null == mItem)
            return;
        GeoGeometry geom = mItem.getGeoGeometry();
        if(null == geom)
            return;

        DrawItems drawItems = mDrawItems.zoom(currentFocusLocation, scale);

        switch (geom.getType()) {
            case GeoConstants.GTPoint:
            case GeoConstants.GTMultiPoint:
                drawItems.drawPoints(canvas);
                break;
            case GeoConstants.GTLineString:
            case GeoConstants.GTMultiLineString:
            case GeoConstants.GTPolygon:
            case GeoConstants.GTMultiPolygon:
                drawItems.drawLines(canvas);
                break;
            default:
                break;
        }
    }

    public void addListener(EditEventListener listener)
    {
        if (mListeners != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(EditEventListener listener)
    {
        if (mListeners != null) {
            mListeners.remove(listener);
        }
    }


    public void setToolbar(BottomToolbar toolbar)
    {
        if(null == toolbar || null == mLayer)
            return;

        mCurrentToolbar = toolbar;
        toolbar.setNavigationOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(mHasEdits){
                    mHasEdits = false;
                    mMode = MODE_EDIT;
                    setToolbar(mCurrentToolbar);
                }
                else {
                    for (EditEventListener listener : mListeners) {
                        listener.onFinishEditSession();
                    }

                    setMode(MODE_NONE);
                }
            }
        });

        toolbar.getMenu().clear();

        if(mHasEdits){
            toolbar.setNavigationIcon(R.drawable.ic_action_cancel);
            toolbar.setNavigationContentDescription(R.string.cancel);
            toolbar.inflateMenu(R.menu.save_edits);
        }
        else {
            toolbar.setNavigationIcon(R.drawable.ic_action_apply);
            toolbar.setNavigationContentDescription(R.string.apply);
            switch (mLayer.getGeometryType()) {
                case GeoConstants.GTPoint:
                    toolbar.inflateMenu(R.menu.edit_point);
                    break;
                case GeoConstants.GTMultiPoint:
                    toolbar.inflateMenu(R.menu.edit_multipoint);
                    break;
                case GeoConstants.GTLineString:
                    toolbar.inflateMenu(R.menu.edit_line);
                    break;
                case GeoConstants.GTMultiLineString:
                    //toolbar.inflateMenu(R.menu.edit_multiline);
                    break;
                case GeoConstants.GTPolygon:
                    toolbar.inflateMenu(R.menu.edit_polygon);
                    break;
                case GeoConstants.GTMultiPolygon:
                    //toolbar.inflateMenu(R.menu.edit_multipolygon);
                    break;
                case GeoConstants.GTGeometryCollection:
                default:
                    break;
            }
        }

        toolbar.setOnMenuItemClickListener(new BottomToolbar.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
                if(menuItem.getItemId() == R.id.menu_edit_save){
                    mHasEdits = false;
                    //TODO: save changes and show attributes edit activity
                }
                return true;
            }
        });

    }


    @Override
    public Bundle onSaveState()
    {
        Bundle bundle = super.onSaveState();
        bundle.putInt(BUNDLE_KEY_TYPE, mType);
        bundle.putInt(BUNDLE_KEY_MODE, mMode);
        if(null == mLayer){
            bundle.putInt(BUNDLE_KEY_LAYER, Constants.NOT_FOUND);
        }
        else {
            bundle.putInt(BUNDLE_KEY_LAYER, mLayer.getId());
        }

        if(null == mItem){
            bundle.putLong(BUNDLE_KEY_FEATURE, Constants.NOT_FOUND);
        }
        else{
            bundle.putLong(BUNDLE_KEY_FEATURE, mItem.getId());
        }
        return bundle;
    }


    @Override
    public void onRestoreState(Bundle bundle)
    {
        if(null != bundle){
            int type = bundle.getInt(BUNDLE_KEY_TYPE);
            if(mType == type) {
                mMode = bundle.getInt(BUNDLE_KEY_MODE);
                int layerId = bundle.getInt(BUNDLE_KEY_LAYER);
                ILayer layer = mMapViewOverlays.getLayerById(layerId);
                mLayer = null;
                if (null != layer && layer instanceof VectorLayer) {
                    mLayer = (VectorLayer)layer;
                    long featureId = bundle.getLong(BUNDLE_KEY_FEATURE);
                    for (VectorCacheItem item : mLayer.getVectorCache()) {
                        if (item.getId() == featureId) {
                            mItem = item;
                            break;
                        }
                    }
                }
                else {
                    mItem = null;
                }
            }
        }
        super.onRestoreState(bundle);
    }


    public void deleteItem() {
        final long itemId = mItem.getId();
        mMapViewOverlays.setDelay(DELAY);
        mLayer.deleteCacheItem(itemId);
        setFeature(mLayer, null);
        //mMapViewOverlays.postInvalidate(); //remove selection the object still exist

        new UndoBarController.UndoBar((android.app.Activity) mContext)
                .message(mContext.getString(R.string.delete_done))
                .listener(new UndoBarController.AdvancedUndoListener()
        {
            @Override
            public void onHide(
                    @Nullable
                    Parcelable parcelable)
            {
                mMapViewOverlays.setSkipNextDraw(true);
                mLayer.delete(VectorLayer.FIELD_ID + " = ?", new String[]{itemId + ""});
                setFeature(null, null);
            }


            @Override
            public void onClear(
                    @NonNull
                    Parcelable[] parcelables)
            {

            }


            @Override
            public void onUndo(
                    @Nullable
                    Parcelable parcelable)
            {
                mMapViewOverlays.setDelay(DELAY);
                mLayer.restoreCacheItem();
                setFeature(null, null);
            }
        }).show();
    }


    @Override
    public void onLongPress(MotionEvent event)
    {
        //TODO: do we need some actions on long press on point or geometry?
    }


    /**
     * Select point in current geometry or new geometry from current layer
     * @param event Motion event
     */
    @Override
    public void onSingleTapUp(MotionEvent event)
    {
        if(mHasEdits)
        {
            //TODO: Ask to save current changes
        }
        double dMinX = event.getX() - mTolerancePX;
        double dMaxX = event.getX() + mTolerancePX;
        double dMinY = event.getY() - mTolerancePX;
        double dMaxY = event.getY() + mTolerancePX;
        GeoEnvelope screenEnv = new GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY);
        //1. search current geometry point
        if(mDrawItems.intersects(screenEnv)) {
            return;
        }

        //2 select another geometry
        GeoEnvelope mapEnv = mMapViewOverlays.screenToMap(screenEnv);
        if(null == mapEnv)
            return;
        List<VectorCacheItem> items = mLayer.query(mapEnv);
        if(items.isEmpty()){
            return;
        }
        mItem = items.get(0);
        mDrawItems.setSelectedPoint(0);
        mDrawItems.setSelectedRing(0);

        mMapViewOverlays.postInvalidate();
    }


    @Override
    public void panStart(MotionEvent event)
    {
        if(mMode == MODE_EDIT) {

            //if pan current selected point

            double dMinX = event.getX() - mTolerancePX * 2 - mAnchorTolerancePX;
            double dMaxX = event.getX() + mTolerancePX;
            double dMinY = event.getY() - mTolerancePX * 2 - mAnchorTolerancePX;
            double dMaxY = event.getY() + mTolerancePX;
            GeoEnvelope screenEnv = new GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY);
            //1. search current geometry point
            if(mDrawItems.intersects(screenEnv)) {
                PointF tempPoint = mDrawItems.getSelectedPoint();
                mTempPointOffset = new PointF(tempPoint.x - event.getX(), tempPoint.y - event.getY());
                mChangedGeometry = mItem.getGeoGeometry().clone();
                mMapViewOverlays.setLockMap(true);
                mMode = MODE_CHANGE;
            }
            else if(mHasEdits){
                //TODO: transfer changes from mDrawItems to mChangedGeometry
            }
        }
    }


    @Override
    public void panMoveTo(MotionEvent e)
    {
        if(mMode == MODE_CHANGE) {
            mDrawItems.setSelectedPointValue(e.getX() + mTempPointOffset.x, e.getY() + mTempPointOffset.y);
        }
    }


    @Override
    public void panStop()
    {
        if(mMode == MODE_CHANGE) {
            mMapViewOverlays.setLockMap(false);
            mHasEdits = true;

            setToolbar(mCurrentToolbar);
            mMode = MODE_EDIT;
            //TODO: create new toolbar with save and cancel buttons
        }
    }


    @Override
    public void onLayerAdded(int id)
    {

    }


    @Override
    public void onLayerDeleted(int id)
    {
        //TODO: if delete edited layer cancel edit session
    }


    @Override
    public void onLayerChanged(int id)
    {

    }


    @Override
    public void onExtentChanged(
            float zoom,
            GeoPoint center)
    {

    }


    @Override
    public void onLayersReordered()
    {

    }


    @Override
    public void onLayerDrawFinished(
            int id,
            float percent)
    {

    }


    protected class DrawItems{
        List<float[]> mDrawItemsVertex;
        List<float[]> mDrawItemsEdge;

        public static final int TYPE_VERTEX = 1;
        public static final int TYPE_EDGE   = 2;

        protected int mSelectedRing, mSelectedPoint;


        public DrawItems()
        {
            mDrawItemsVertex = new ArrayList<>();
            mDrawItemsEdge = new ArrayList<>();

            mSelectedRing = Constants.NOT_FOUND;
            mSelectedPoint = Constants.NOT_FOUND;
        }


        public void setSelectedRing(int selectedRing)
        {
            mSelectedRing = selectedRing;
        }


        public void setSelectedPoint(int selectedPoint)
        {
            mSelectedPoint = selectedPoint;
        }


        public void addItems(
                int ring,
                float[] points,
                int type)
        {
            if(type == TYPE_VERTEX)
                mDrawItemsVertex.add(ring, points);
            else if(type == TYPE_EDGE)
                mDrawItemsEdge.add(ring, points);
        }


        public void clear()
        {
            mDrawItemsVertex.clear();
            mDrawItemsEdge.clear();
        }

        public DrawItems zoom(PointF location, float scale){
            DrawItems drawItems = new DrawItems();
            drawItems.setSelectedRing(mSelectedRing);
            drawItems.setSelectedPoint(mSelectedPoint);

            int count = 0;
            for (float[] items : mDrawItemsVertex) {
                float[] newItems = new float[items.length];
                for(int i = 0; i < items.length - 1; i += 2){
                    newItems[i] = items[i] - (1 - scale) * (items[i] + location.x);
                    newItems[i + 1] = items[i + 1] - (1 - scale) * (items[i + 1] + location.y);
                }
                drawItems.addItems(count++, newItems, TYPE_VERTEX);
            }

            count = 0;
            for (float[] items : mDrawItemsEdge) {
                float[] newItems = new float[items.length];
                for(int i = 0; i < items.length - 1; i += 2){
                    newItems[i] = items[i] - (1 - scale) * (items[i] + location.x);
                    newItems[i + 1] = items[i + 1] - (1 - scale) * (items[i + 1] + location.y);
                }
                drawItems.addItems(count++, newItems, TYPE_EDGE);
            }

            return drawItems;
        }

        public DrawItems pan(PointF offset){
            DrawItems drawItems = new DrawItems();
            drawItems.setSelectedRing(mSelectedRing);
            drawItems.setSelectedPoint(mSelectedPoint);

            int count = 0;
            for (float[] items : mDrawItemsVertex) {
                float[] newItems = new float[items.length];
                for(int i = 0; i < items.length - 1; i += 2){
                    newItems[i] = items[i] - offset.x;
                    newItems[i + 1] = items[i + 1] - offset.y;
                }
                drawItems.addItems(count++, newItems, TYPE_VERTEX);
            }

            count = 0;
            for (float[] items : mDrawItemsEdge) {
                float[] newItems = new float[items.length];
                for(int i = 0; i < items.length - 1; i += 2){
                    newItems[i] = items[i] - offset.x;
                    newItems[i + 1] = items[i + 1] - offset.y;
                }
                drawItems.addItems(count++, newItems, TYPE_EDGE);
            }

            return drawItems;
        }


        public void drawPoints(Canvas canvas)
        {
            for (float[] items : mDrawItemsVertex) {

                mPaint.setColor(mOutlineColor);
                mPaint.setStrokeWidth(VERTEX_RADIUS + 2);
                canvas.drawPoints(items, mPaint);

                mPaint.setColor(mFillColor);
                mPaint.setStrokeWidth(VERTEX_RADIUS);
                canvas.drawPoints(items, mPaint);
            }

            //draw selected point
            if(mSelectedRing != Constants.NOT_FOUND && mSelectedPoint != Constants.NOT_FOUND) {

                float[] items = mDrawItemsVertex.get(mSelectedRing);
                if(null != items) {
                    mPaint.setColor(mSelectColor);
                    mPaint.setStrokeWidth(VERTEX_RADIUS);

                    canvas.drawPoint(items[mSelectedPoint], items[mSelectedPoint + 1], mPaint);

                    float anchorX = items[mSelectedPoint] + mAnchorRectOffsetX;
                    float anchorY = items[mSelectedPoint + 1] + mAnchorRectOffsetY;
                    canvas.drawBitmap(mAnchor, anchorX, anchorY, null);
                }
            }
        }

        public void drawLines(Canvas canvas)
        {
            for (float[] items : mDrawItemsVertex) {

                mPaint.setColor(mFillColor);
                mPaint.setStrokeWidth(LINE_WIDTH);
                canvas.drawLines(items, mPaint);

                if(mMode == MODE_EDIT) {
                    mPaint.setColor(mOutlineColor);
                    mPaint.setStrokeWidth(VERTEX_RADIUS + 2);
                    canvas.drawPoints(items, mPaint);

                    mPaint.setColor(mFillColor);
                    mPaint.setStrokeWidth(VERTEX_RADIUS);
                    canvas.drawPoints(items, mPaint);
                }
            }

            if(mMode == MODE_EDIT) {
                for (float[] items : mDrawItemsEdge) {

                    mPaint.setColor(mOutlineColor);
                    mPaint.setStrokeWidth(EDGE_RADIUS + 2);
                    canvas.drawPoints(items, mPaint);

                    mPaint.setColor(mFillColor);
                    mPaint.setStrokeWidth(EDGE_RADIUS);
                    canvas.drawPoints(items, mPaint);
                }
            }

            //draw selected point
            if(mSelectedRing != Constants.NOT_FOUND && mSelectedPoint != Constants.NOT_FOUND) {

                float[] items = mDrawItemsVertex.get(mSelectedRing);
                if(null != items) {
                    mPaint.setColor(mSelectColor);
                    mPaint.setStrokeWidth(VERTEX_RADIUS);

                    canvas.drawPoint(items[mSelectedPoint], items[mSelectedPoint + 1], mPaint);
                }
            }
        }


        public boolean intersects(GeoEnvelope screenEnv)
        {
            int ring = 0;
            int point = 0;
            for (float[] items : mDrawItemsVertex) {
                for(int i = 0; i < items.length - 1; i += 2){
                    if(screenEnv.contains(new GeoPoint(items[i], items[i + 1]))){
                        mSelectedRing = ring;
                        mSelectedPoint = point;
                        return true;
                    }
                    point++;

                }
                ring++;
            }

            if(mMode == MODE_EDIT) {
                ring = 0;
                point = 0;
                for (float[] items : mDrawItemsEdge) {
                    for (int i = 0; i < items.length - 1; i += 2) {
                        if(screenEnv.contains(new GeoPoint(items[i], items[i + 1]))){
                            //mSelectedRing = ring;
                            //mSelectedPoint = point;
                            //TODO: store PointF and on pan this point add it to the vertex array
                            //and on pan stop add new edge points
                            return true;
                        }
                        point++;
                    }
                    ring++;
                }
            }
            return false;
        }


        public PointF getSelectedPoint()
        {
            float[] points = mDrawItemsVertex.get(mSelectedRing);
            if(null == points)
                return null;
            return new PointF(points[mSelectedPoint], points[mSelectedPoint + 1]);
        }

        public void setSelectedPointValue(float x, float y){
            float[] points = mDrawItemsVertex.get(mSelectedRing);
            if(null != points){
                points[mSelectedPoint] = x;
                points[mSelectedPoint + 1] = y;
            }
        }
    }
}
