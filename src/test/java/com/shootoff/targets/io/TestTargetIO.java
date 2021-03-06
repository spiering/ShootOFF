/*
 * Copyright (c) 2015 phrack. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.shootoff.targets.io;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;

import org.junit.Before;
import org.junit.Test;

import com.shootoff.targets.EllipseRegion;
import com.shootoff.targets.ImageRegion;
import com.shootoff.targets.PolygonRegion;
import com.shootoff.targets.RectangleRegion;
import com.shootoff.targets.TargetRegion;

public class TestTargetIO {
	private List<Node> regions = new ArrayList<Node>();
	private ImageRegion img;
	private RectangleRegion rec;
	private EllipseRegion ell;
	private PolygonRegion pol;

	@Before
	public void setUp() {
		Map<String, String> imgTags = new HashMap<String, String>();
		imgTags.put("1", "2");
		img = new ImageRegion(6, 6, new File("targets" + File.separator + "plate.gif"));
		img.setTags(imgTags);
		
		Map<String, String> recTags = new HashMap<String, String>();
		recTags.put("a", "b");
		recTags.put("c", "d");
		rec = new RectangleRegion(10, 40, 20, 90);
		rec.setFill(Color.ORANGE);
		rec.setTags(recTags);

		Map<String, String> ellTags = new HashMap<String, String>();
		ellTags.put("name", "value");
		ell = new EllipseRegion(0, 20, 5, 5);
		ell.setFill(Color.RED);
		ell.setTags(ellTags);
		
		Map<String, String> polTags = new HashMap<String, String>();
		polTags.put("points", "3");
		pol = new PolygonRegion(300, 0, 400, 30, 300, 100);
		pol.setTags(polTags);
		
		regions.add(img);
		regions.add(rec);
		regions.add(ell);
		regions.add(pol);
	}
	
	@Test
	public void testXMLSerialization() {
		File tempXMLTarget = new File("temp.target");
		TargetIO.saveTarget(regions, tempXMLTarget);
				
		Optional<Group> target = TargetIO.loadTarget(tempXMLTarget);
		
		assertTrue(target.isPresent());
		
		Group targetGroup = target.get();
		
		assertEquals(4, targetGroup.getChildren().size());
		
		for (Node node : targetGroup.getChildren()) {
			TargetRegion region = (TargetRegion)node;
			
			switch (region.getType()) {
			case IMAGE:
				ImageRegion img = (ImageRegion)region;
				assertEquals(this.img.getX(), img.getX(), 0.5);
				assertEquals(this.img.getY(), img.getY(), 0.5);
				assertEquals(this.img.getImageFile(), img.getImageFile());
				assertEquals("2", this.img.getTag("1"));
				break;
			case RECTANGLE:
				RectangleRegion rec = (RectangleRegion)region;
				assertEquals(this.rec.getX(), rec.getX(), 0.5);
				assertEquals(this.rec.getY(), rec.getY(), 0.5);
				assertEquals(this.rec.getWidth(), rec.getWidth(), 0.5);
				assertEquals(this.rec.getHeight(), rec.getHeight(), 0.5);
				assertEquals(this.rec.getFill(), rec.getFill());
				assertEquals("b", this.rec.getTag("a"));
				assertEquals("d", this.rec.getTag("c"));
				break;
			case ELLIPSE:
				EllipseRegion ell = (EllipseRegion)region;
				assertEquals(this.ell.getCenterX(), ell.getCenterX(), 0.5);
				assertEquals(this.ell.getCenterY(), ell.getCenterY(), 0.5);
				assertEquals(this.ell.getRadiusX(), ell.getRadiusX(), 0.5);
				assertEquals(this.ell.getRadiusY(), ell.getRadiusY(), 0.5);
				assertEquals(this.ell.getFill(), ell.getFill());
				break;
			case POLYGON:
				PolygonRegion pol = (PolygonRegion)region;
				assertEquals(this.pol.getPoints(), pol.getPoints());
				assertEquals(this.pol.getFill(), pol.getFill());
				break;
			}
		}
		
		tempXMLTarget.delete();
	}

}
