/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2006-2006 LucidEra, Inc.
// Copyright (C) 2006-2006 The Eigenbase Project
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

#include "fennel/common/CommonPreamble.h"
#include "fennel/exec/ExecStreamBufAccessor.h"
#include "fennel/lucidera/bitmap/LbmSeqSegmentReader.h"

FENNEL_BEGIN_CPPFILE("$Id$");

void LbmSeqSegmentReader::init(
    SharedExecStreamBufAccessor &pInAccessorInit,
    TupleData &bitmapSegTuple)
{
    LbmSegmentReaderBase::init(pInAccessorInit, bitmapSegTuple);
}

ExecStreamResult LbmSeqSegmentReader::readSegmentAndAdvance(
    LcsRid &byteNum, PBuffer &byteSeg, uint &len)
{
    if (pSegDescStart >= pSegDescEnd) {
        // read a new bitmap segment tuple from the input stream
        ExecStreamResult rc = readBitmapSegTuple();
        if (rc != EXECRC_YIELD) {
            return rc;
        }

        // single bitmap case
        if (!pSegDescStart) {
            byteNum = byteSegOffset;
            byteSeg = pSegStart;
            len = byteSegLen;
            return EXECRC_YIELD;
        }
    }

    // advance to the next segment and set the return values
    advanceSegment();
    byteNum = byteSegOffset;
    byteSeg = pSegStart;
    len = byteSegLen;

    return EXECRC_YIELD;
}

LcsRid LbmSeqSegmentReader::getSrid()
{
    return startRID;
}

FENNEL_END_CPPFILE("$Id$");

// End LbmSeqSegmentReader.cpp