/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2005-2005 LucidEra, Inc.
// Copyright (C) 2005-2005 The Eigenbase Project
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
#include "fennel/lucidera/colstore/LcsClusterDump.h"
#include "fennel/lucidera/colstore/LcsClusterNode.h"
#include "fennel/lucidera/colstore/LcsBitOps.h"
#include "fennel/tuple/TupleData.h"
#include "fennel/common/TraceSource.h"
#include <stdarg.h>

using namespace std;

FENNEL_BEGIN_CPPFILE("$Id$");

const uint lnLen = 80;          // line kength
const uint lnSep = 1;           // hex/char seperator width
const uint lnValIdx = 6;        // lenght of value index field
const uint lnColLen = 6;        // length of column length field
const uint lnByte = 3;          // length of byte expressed in hex char inc.
                                // spaces
const uint lnChar = 1;          // length of a char field

// number of hex bytes in a line 
const uint nByte = (lnLen - lnValIdx - lnColLen - lnSep) / (lnByte + lnChar);

// offset to byte fields
const uint oByte = lnValIdx + lnColLen;

// offset to string field
const uint oStr = oByte + nByte * lnByte + lnSep;

// maximum read at one time
const uint MaxReadBatch = 64;

LcsClusterDump::LcsClusterDump(TraceLevel traceLevelInit,
                               SharedTraceTarget pTraceTargetInit,
                               string nameInit)
                               : TraceSource(pTraceTargetInit, nameInit)
{
    traceLevel = traceLevelInit;
}

// Dump page contents
void LcsClusterDump::dump(uint64_t pageId, PBuffer pBlock, uint szBlock)
{
    PLcsClusterNode pHdr;
    uint i, j, k;
    uint16_t *pO;
    PBuffer pR;

    uint count;

    uint nBits;
    PLcsBatchDir pBatch;
    WidthVec w;                     // bitVec width vector
    PtrVec p;                       // bitVec offsets
    uint iV;                        // number of bit vectors
    uint8_t *pBit;
    uint16_t v[MaxReadBatch];       // temporary space to store row indexes
    char *mode;

    // print header
    
    pHdr = (PLcsClusterNode) pBlock;
    callTrace("Cluster Page Dump - PageId %ld", pageId);
    callTrace("-----------------------------");
    callTrace("Header");
    callTrace("------");
    callTrace("nColumn:          %5u", pHdr->nColumn);
    callTrace("firstRid:         %5u", pHdr->firstRID);
    callTrace("oBatch:           %5u", pHdr->oBatch);
    callTrace("nBatch:           %5u", pHdr->nBatch);
    for (i = 0; i < pHdr->nColumn; i++) {
        callTrace("lastVal[%d]:       %5u", i, pHdr->lastVal[i]);
        callTrace("firstVal[%d]:      %5u", i, pHdr->firstVal[i]);
        callTrace("nVal[%d]:          %5u", i, pHdr->nVal[i]);
        callTrace("delta[%d]:         %5u", i, pHdr->delta[i]);
    }

    callTrace("#############################################################");

    // print individual batch contents

    pBatch = (PLcsBatchDir) (pBlock + pHdr->oBatch);
    for (i = 0; i < pHdr->nBatch; i++) {
        switch (pBatch[i].mode) {
        case LCS_COMPRESSED:
            mode = "Compressed";
            break;
        case LCS_VARIABLE:
            mode = "Variable";
            break;
        case LCS_FIXED:
            mode = "Fixed";
            break;
        default:
            permAssert(false);
        }
        callTrace("Batch #%2u (%s)", i + 1, mode);
        callTrace("--------------------");
        callTrace("mode:             %5u", pBatch[i].mode);
        callTrace("nRow:             %5u", pBatch[i].nRow);
        callTrace("nVal:             %5u", pBatch[i].nVal);
        callTrace("nValHighMark:     %5u", pBatch[i].nValHighMark);
        callTrace("oVal:             %5u", pBatch[i].oVal);
        callTrace("oLastValHighMark: %5u", pBatch[i].oLastValHighMark);
        callTrace("recSize:          %5u", pBatch[i].recSize);

        if (pBatch[i].mode == LCS_COMPRESSED) {

            nBits = CalcWidth(pBatch[i].nVal);

            // calculate the bit vector widthes, sum(w[i]) is nBits 
            iV = BitVecWidth(nBits, w);

            // this is where the bit vectors start
            pBit = pBlock + pBatch[i].oVal + pBatch[i].nVal * sizeof(uint16_t);

            // nBytes are taken by the bit vectors
            BitVecPtr(pBatch[i].nRow, iV, w, p, pBit);

            callTrace("Rows");
            callTrace("----");

            for (j = 0; j < pBatch[i].nRow;) {

                char buf[lnLen + 1];
                int bufidx = 0;

                buf[0]= 0;
                count = min(uint(pBatch[i].nRow - j), MaxReadBatch);
                // read rows j to j+count -1
                ReadBitVecs(v, iV, w, p, j, count);     

                for (k = 0; k < count; k++, j++) {
                    if ((j % 8) == 0) {
                        if (j > 0)
                            callTrace(buf);
                        sprintf(buf, "%5u: ", j);
                        bufidx = 7;
                    }

                    sprintf(buf + bufidx, "%5u ", (uint) v[k]);
                    bufidx += 6;
                }
                callTrace(buf);
            }

            callTrace("Batch Values");
            callTrace("------------");
            pO = (uint16_t *) (pBlock + pBatch[i].oVal);
            for (j = 0; j < pBatch[i].nVal; j++)
                fprintVal(j, pBlock + pO[j]);

        } else if (pBatch[i].mode == LCS_FIXED) {
            // fixed size rows
            callTrace("Fixed Size Rows");
            callTrace("---------------");
            pR = pBlock + pBatch[i].oVal;
            for (j = 0; j < pBatch[i].nRow; j++) {
                fprintVal(j, pR);
                pR += pBatch[i].recSize;
            }
        } else {
            // variable size rows
            callTrace("Variable Size Rows");
            callTrace("------------------");
            pO = (uint16_t *) (pBlock + pBatch[i].oVal);
            for (j = 0; j < pBatch[i].nRow; j++)
                fprintVal(j, pBlock + pO[j]);
        }
        callTrace("#############################################################");
    }

    // print values, dereferencing them through the header values

    callTrace("Value List at the Bottom of the Page");
    callTrace("------------------------------------");
    for (i = 0; i < pHdr->nColumn; i++) {
        callTrace("Column #%2u", i);
        callTrace("------------");
        if (pHdr->lastVal[i] < szBlock) {
            pR = pBlock + pHdr->lastVal[i];
            for (j = pHdr->nVal[i]; j > 0; j--)
                pR = fprintVal(j, pR);
        } else
            callTrace("NONE.");
    }
}

// Interface into fennel trace to print a formatted string
void LcsClusterDump::callTrace(char *format, ...)
{
    char buf[lnLen + 1];
    string str;
    va_list args;

    va_start(args, format);
    vsprintf(buf, format, args);
    va_end(args);

    str += buf;
    FENNEL_TRACE(traceLevel, str);
}

// print a formatted value, return pointer to next value
PBuffer LcsClusterDump::fprintVal(uint idx, PBuffer pV)
{
    uint j, sz, k, l;
    PBuffer p = pV;
    char st[lnLen + 1];

    st[lnLen] = 0;
    memset(st, ' ', lnLen);
    callTrace("%05u:", idx);
    
    sz = TupleDatum(p).getStorageLength();
    l = sprintf(st + lnValIdx, "%4u: ", sz);
    st[lnValIdx + l] = 0;

    for(j = 0 ; j < sz; j++) {
        if (j && ((j % nByte) == 0)) {  
            callTrace(st);
            memset(st, ' ', lnLen);
        }
        
        k = oByte + lnByte * (j % nByte);
        l = sprintf(st + k, "%2X ", p[j]);
        st[k + l] = ' '; 
        st[oStr + (j % nByte)] =  isprint(p[j]) ? p[j] : '.';
    }

    callTrace(st);

    p += sz;
    return p;
}

FENNEL_END_CPPFILE("$Id$");

// End LcsClusterDump.cpp