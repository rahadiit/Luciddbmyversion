/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2006-2006 The Eigenbase Project
// Copyright (C) 2006-2006 Disruptive Tech
// Copyright (C) 2006-2006 LucidEra, Inc.
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version approved by The Eigenbase Project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.eigenbase.test;

import junit.framework.TestCase;

import org.eigenbase.sarg.*;
import org.eigenbase.rex.*;
import org.eigenbase.reltype.*;
import org.eigenbase.sql.type.*;

import java.math.*;
import java.util.*;

/**
 * SargTest tests the {@link org.eigenbase.sarg} class library.
 *
 *<p>
 *
 * NOTE jvs 17-Jan-2006:  This class lives in org.eigenbase.test rather
 * than org.eigenbase.sarg by design:  we want to make sure we're only
 * testing via the public interface.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class SargTest extends TestCase
{
    enum Zodiac {
        AQUARIUS,
        ARIES,
        CANCER,
        CAPRICORN,
        GEMINI,
        LEO,
        LIBRA,
        PISCES,
        SAGITTARIUS,
        SCORPIO,
        TAURUS,
        VIRGO
    };
    
    private SargFactory sargFactory;
    
    private RelDataType intType;
    
    private RelDataType stringType;
    
    private RexNode intLiteral7;
    
    private RexNode intLiteral8point5;
    
    private RexNode intLiteral490;
    
    private SargIntervalExpr [] exprs;
    
    /**
     * Initializes a new SargTest.
     *
     * @param testCaseName JUnit test case name
     */
    public SargTest(String testCaseName)
        throws Exception
    {
        super(testCaseName);
    }

    // implement TestCase
    public void setUp()
    {
        // create some reusable fixtures
        
        RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl();
        intType = typeFactory.createSqlType(SqlTypeName.Integer);
        stringType = typeFactory.createSqlType(SqlTypeName.Varchar, 20);
        
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        intLiteral7 = rexBuilder.makeExactLiteral(
            new BigDecimal(7), intType);
        intLiteral490 = rexBuilder.makeExactLiteral(
            new BigDecimal(490), intType);
        intLiteral8point5 = rexBuilder.makeExactLiteral(
            new BigDecimal("8.5"), intType);

        sargFactory = new SargFactory(rexBuilder);
    }
    
    public void testDefaultEndpoint()
    {
        SargMutableEndpoint ep = sargFactory.newEndpoint(intType);
        assertEquals("-infinity", ep.toString());
    }
    
    public void testInfiniteEndpoint()
    {
        SargMutableEndpoint ep = sargFactory.newEndpoint(intType);
        ep.setInfinity(1);
        assertEquals("+infinity", ep.toString());
        ep.setInfinity(-1);
        assertEquals("-infinity", ep.toString());
    }

    public void testFiniteEndpoint()
    {
        SargMutableEndpoint ep = sargFactory.newEndpoint(intType);
        
        ep.setFinite(
            SargBoundType.LOWER,
            SargStrictness.OPEN,
            intLiteral7);
        assertEquals("> 7", ep.toString());
        
        ep.setFinite(
            SargBoundType.LOWER,
            SargStrictness.CLOSED,
            intLiteral7);
        assertEquals(">= 7", ep.toString());
        
        ep.setFinite(
            SargBoundType.UPPER,
            SargStrictness.OPEN,
            intLiteral7);
        assertEquals("< 7", ep.toString());
        
        ep.setFinite(
            SargBoundType.UPPER,
            SargStrictness.CLOSED,
            intLiteral7);
        assertEquals("<= 7", ep.toString());

        // after rounding, "> 8.5" is equivalent to ">= 9" over the domain
        // of integers
        ep.setFinite(
            SargBoundType.LOWER,
            SargStrictness.OPEN,
            intLiteral8point5);
        assertEquals(">= 9", ep.toString());
        
        ep.setFinite(
            SargBoundType.LOWER,
            SargStrictness.CLOSED,
            intLiteral8point5);
        assertEquals(">= 9", ep.toString());

        ep.setFinite(
            SargBoundType.UPPER,
            SargStrictness.OPEN,
            intLiteral8point5);
        assertEquals("< 9", ep.toString());
        
        ep.setFinite(
            SargBoundType.UPPER,
            SargStrictness.CLOSED,
            intLiteral8point5);
        assertEquals("< 9", ep.toString());
    }

    public void testNullEndpoint()
    {
        SargMutableEndpoint ep = sargFactory.newEndpoint(intType);

        ep.setFinite(
            SargBoundType.LOWER,
            SargStrictness.OPEN,
            sargFactory.newNullLiteral());
        assertEquals("> null", ep.toString());
    }

    public void testTouchingEndpoint()
    {
        SargMutableEndpoint ep1 = sargFactory.newEndpoint(intType);
        SargMutableEndpoint ep2 = sargFactory.newEndpoint(intType);

        // "-infinity" does not touch "-infinity" (seems like something you
        // could argue for hours late at night in a college dorm)
        assertFalse(ep1.isTouching(ep2));
        
        // "< 7" does not touch "> 7"
        ep1.setFinite(
            SargBoundType.UPPER,
            SargStrictness.OPEN,
            intLiteral7);
        ep2.setFinite(
            SargBoundType.LOWER,
            SargStrictness.OPEN,
            intLiteral7);
        assertFalse(ep1.isTouching(ep2));
        assertTrue(ep1.compareTo(ep2) < 0);

        // "< 7" does touch ">= 7"
        ep2.setFinite(
            SargBoundType.LOWER,
            SargStrictness.CLOSED,
            intLiteral7);
        assertTrue(ep1.isTouching(ep2));
        assertTrue(ep1.compareTo(ep2) < 0);

        // "<= 7" does touch ">= 7"
        ep1.setFinite(
            SargBoundType.LOWER,
            SargStrictness.CLOSED,
            intLiteral7);
        assertTrue(ep1.isTouching(ep2));
        assertEquals(0, ep1.compareTo(ep2));

        // "<= 7" does not touch ">= 490"
        ep2.setFinite(
            SargBoundType.LOWER,
            SargStrictness.CLOSED,
            intLiteral490);
        assertFalse(ep1.isTouching(ep2));
        assertTrue(ep1.compareTo(ep2) < 0);
    }

    public void testDefaultIntervalExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);
        assertEquals("(-infinity, +infinity)", interval.toString());
    }

    public void testPointExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);
        interval.setPoint(intLiteral7);
        assertTrue(interval.isPoint());
        assertFalse(interval.isUnconstrained());
        assertFalse(interval.isEmpty());
        assertEquals("[7]", interval.toString());
        assertEquals("[7]", interval.evaluate().toString());
    }

    public void testRangeIntervalExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);

        interval.setLower(intLiteral7, SargStrictness.CLOSED);
        interval.setUpper(intLiteral490, SargStrictness.CLOSED);
        assertRange(interval);
        assertEquals("[7, 490]", interval.toString());
        assertEquals("[7, 490]", interval.evaluate().toString());

        interval.unsetLower();
        assertRange(interval);
        assertEquals("(-infinity, 490]", interval.toString());
        assertEquals("(null, 490]", interval.evaluate().toString());
        
        interval.setLower(intLiteral7, SargStrictness.CLOSED);
        interval.unsetUpper();
        assertRange(interval);
        assertEquals("[7, +infinity)", interval.toString());
        assertEquals("[7, +infinity)", interval.evaluate().toString());

        interval.setUpper(intLiteral490, SargStrictness.OPEN);
        assertRange(interval);
        assertEquals("[7, 490)", interval.toString());
        assertEquals("[7, 490)", interval.evaluate().toString());

        interval.setLower(intLiteral7, SargStrictness.OPEN);
        assertRange(interval);
        assertEquals("(7, 490)", interval.toString());
        assertEquals("(7, 490)", interval.evaluate().toString());
    }

    private void assertRange(SargIntervalExpr interval)
    {
        assertFalse(interval.isPoint());
        assertFalse(interval.isUnconstrained());
        assertFalse(interval.isEmpty());
    }

    public void testNullExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);
        interval.setNull();
        assertTrue(interval.isPoint());
        assertFalse(interval.isUnconstrained());
        assertFalse(interval.isEmpty());
        assertEquals("[null] NULL_MATCHES_NULL", interval.toString());
        assertEquals("[null]", interval.evaluate().toString());
    }

    public void testEmptyExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);
        interval.setEmpty();
        assertTrue(interval.isEmpty());
        assertFalse(interval.isUnconstrained());
        assertEquals("()", interval.toString());
        assertEquals("()", interval.evaluate().toString());
    }

    public void testUnconstrainedExpr()
    {
        SargIntervalExpr interval = sargFactory.newIntervalExpr(intType);
        interval.setEmpty();
        assertFalse(interval.isUnconstrained());
        interval.setUnconstrained();
        assertTrue(interval.isUnconstrained());
        assertFalse(interval.isEmpty());
        assertEquals("(-infinity, +infinity)", interval.toString());
        assertEquals("(-infinity, +infinity)", interval.evaluate().toString());
    }

    public void testSetExpr()
    {
        SargIntervalExpr interval1 = sargFactory.newIntervalExpr(intType);
        SargIntervalExpr interval2 = sargFactory.newIntervalExpr(intType);

        interval1.setLower(intLiteral7, SargStrictness.OPEN);
        interval2.setUpper(intLiteral490, SargStrictness.OPEN);

        SargSetExpr intersectExpr = sargFactory.newSetExpr(
            intType, SargSetOperator.INTERSECTION);
        intersectExpr.addChild(interval1);
        intersectExpr.addChild(interval2);
        assertEquals(
            "INTERSECTION( (7, +infinity) (-infinity, 490) )",
            intersectExpr.toString());
        assertEquals(
            "(7, 490)",
            intersectExpr.evaluate().toString());
        
        SargSetExpr unionExpr = sargFactory.newSetExpr(
            intType, SargSetOperator.UNION);
        unionExpr.addChild(interval1);
        unionExpr.addChild(interval2);
        assertEquals(
            "UNION( (7, +infinity) (-infinity, 490) )",
            unionExpr.toString());
        assertEquals(
            "(null, +infinity)",
            unionExpr.evaluate().toString());
        
        SargSetExpr complementExpr = sargFactory.newSetExpr(
            intType, SargSetOperator.COMPLEMENT);
        complementExpr.addChild(interval1);
        assertEquals(
            "COMPLEMENT( (7, +infinity) )",
            complementExpr.toString());
        assertEquals(
            "(-infinity, 7]",
            complementExpr.evaluate().toString());
    }

    public void testUnion()
    {
        exprs = new SargIntervalExpr[11];
        for (int i = 0; i < 11; ++i) {
            exprs[i] = sargFactory.newIntervalExpr(stringType);
        }
        
        exprs[0].setPoint(createCoordinate(Zodiac.AQUARIUS));
        
        exprs[1].setPoint(createCoordinate(Zodiac.LEO));
        
        exprs[2].setUpper(
            createCoordinate(Zodiac.CAPRICORN), SargStrictness.CLOSED);
        
        exprs[3].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.OPEN);
        
        exprs[4].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        
        exprs[5].setNull();
        
        exprs[6].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        exprs[6].setUpper(
            createCoordinate(Zodiac.PISCES), SargStrictness.CLOSED);
        
        exprs[7].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        exprs[7].setUpper(
            createCoordinate(Zodiac.SCORPIO), SargStrictness.CLOSED);
        
        exprs[8].setLower(
            createCoordinate(Zodiac.ARIES), SargStrictness.CLOSED);
        exprs[8].setUpper(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        
        exprs[9].setLower(
            createCoordinate(Zodiac.ARIES), SargStrictness.CLOSED);
        exprs[9].setUpper(
            createCoordinate(Zodiac.GEMINI), SargStrictness.OPEN);

        exprs[10].setEmpty();

        checkUnion(
            0, 2, 5,
            "[null, 'CAPRICORN']");
        
        checkUnion(
            2, 5, 0,
            "[null, 'CAPRICORN']");
        
        checkUnion(
            5, 6, 7,
            "UNION( [null] ['GEMINI', 'SCORPIO'] )");
        
        checkUnion(
            8, 4, 5,
            "UNION( [null] ['ARIES', +infinity) )");

        checkUnion(
            9, 4, 5,
            "UNION( [null] ['ARIES', +infinity) )");
        
        checkUnion(
            7, 8, 9,
            "['ARIES', 'SCORPIO']");
        
        checkUnion(
            6, 7, 10,
            "['GEMINI', 'SCORPIO']");
        
        checkUnion(
            5, 6, 0,
            "UNION( [null] ['AQUARIUS'] ['GEMINI', 'PISCES'] )");
        
        checkUnion(
            10, 9, 5,
            "UNION( [null] ['ARIES', 'GEMINI') )");
        
        checkUnion(
            9, 8, 7,
            "['ARIES', 'SCORPIO']");
        
        checkUnion(
            3, 9, 1,
            "UNION( ['ARIES', 'GEMINI') ('GEMINI', +infinity) )");
    }

    public void testIntersection()
    {
        exprs = new SargIntervalExpr[11];
        for (int i = 0; i < 11; ++i) {
            exprs[i] = sargFactory.newIntervalExpr(stringType);
        }
        
        exprs[0].setUnconstrained();
        
        exprs[1].setPoint(createCoordinate(Zodiac.LEO));
        
        exprs[2].setUpper(
            createCoordinate(Zodiac.CAPRICORN), SargStrictness.CLOSED);
        
        exprs[3].setLower(
            createCoordinate(Zodiac.CANCER), SargStrictness.OPEN);
        
        exprs[4].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        
        exprs[5].setNull();
        
        exprs[6].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        exprs[6].setUpper(
            createCoordinate(Zodiac.PISCES), SargStrictness.CLOSED);
        
        exprs[7].setLower(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        exprs[7].setUpper(
            createCoordinate(Zodiac.SCORPIO), SargStrictness.CLOSED);
        
        exprs[8].setLower(
            createCoordinate(Zodiac.ARIES), SargStrictness.CLOSED);
        exprs[8].setUpper(
            createCoordinate(Zodiac.GEMINI), SargStrictness.CLOSED);
        
        exprs[9].setLower(
            createCoordinate(Zodiac.ARIES), SargStrictness.CLOSED);
        exprs[9].setUpper(
            createCoordinate(Zodiac.GEMINI), SargStrictness.OPEN);

        exprs[10].setEmpty();

        checkIntersection(
            2, 3, 0,
            "('CANCER', 'CAPRICORN']");

        checkIntersection(
            0, 3, 2,
            "('CANCER', 'CAPRICORN']");

        checkIntersection(
            6, 7, 0,
            "['GEMINI', 'PISCES']");

        checkIntersection(
            6, 7, 8,
            "['GEMINI']");

        checkIntersection(
            8, 7, 6,
            "['GEMINI']");

        checkIntersection(
            9, 7, 6,
            "()");
    }
    
    private void checkUnion(
        int i1, int i2, int i3,
        String expected)
    {
        checkSetOp(SargSetOperator.UNION, i1, i2, i3, expected);
    }

    private void checkIntersection(
        int i1, int i2, int i3,
        String expected)
    {
        checkSetOp(SargSetOperator.INTERSECTION, i1, i2, i3, expected);
    }

    private void checkSetOp(
        SargSetOperator setOp,
        int i1, int i2, int i3,
        String expected)
    {
        SargSetExpr setExpr = sargFactory.newSetExpr(
            stringType, setOp);
        setExpr.addChild(exprs[i1]);
        setExpr.addChild(exprs[i2]);
        setExpr.addChild(exprs[i3]);
        assertEquals(expected, setExpr.evaluate().toString());
    }

    private RexNode createCoordinate(Zodiac z)
    {
        return sargFactory.getRexBuilder().makeLiteral(z.toString());
    }
}

// End SargTest.java