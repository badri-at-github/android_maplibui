/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
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

package com.nextgis.maplibui.mapui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.ILayerUI;

import java.io.File;


/**
 * A UI for vector layer
 */
public class VectorLayerUI extends VectorLayer implements ILayerUI
{
    public VectorLayerUI(
            Context context,
            File path)
    {
        super(context, path);
    }


    @Override
    public Drawable getIcon()
    {
        return mContext.getResources().getDrawable(R.drawable.ic_local_vector);
    }


    @Override
    public void changeProperties()
    {

    }


    @Override
    public void showEditForm(Context context)
    {

    }
}