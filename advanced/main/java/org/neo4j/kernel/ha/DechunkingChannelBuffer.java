/**
 * Copyright (c) 2002-2010 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.kernel.ha;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.queue.BlockingReadHandler;

public class DechunkingChannelBuffer implements ChannelBuffer
{
    private final BlockingReadHandler<ChannelBuffer> reader;
    private ChannelBuffer buffer;
    private boolean more;
    private boolean hasMarkedReaderIndex;

    DechunkingChannelBuffer( BlockingReadHandler<ChannelBuffer> reader )
    {
        this.reader = reader;
        readNextChunk();
    }
    
    protected ChannelBuffer readNext()
    {
        try
        {
            return reader.read( MasterClient.READ_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS );
        }
        catch ( IOException e )
        {
            throw new HaCommunicationException( e );
        }
        catch ( InterruptedException e )
        {
            throw new HaCommunicationException( e );
        }
    }

    private void readNextChunkIfNeeded( int bytesPlus )
    {
        if ( buffer.readableBytes() < bytesPlus && more )
        {
            readNextChunk();
        }
    }
    
    private void readNextChunk()
    {
        ChannelBuffer readBuffer = readNext();
        more = readBuffer.readByte() == ChunkingChannelBuffer.CONTINUATION_MORE;
        if ( !more && buffer == null )
        {
            // Optimization: this is the first chunk and it'll be the only chunk
            // in this message.
            buffer = readBuffer;
        }
        else
        {
            buffer = buffer == null ? ChannelBuffers.dynamicBuffer() : buffer;
            discardReadBytes();
            buffer.writeBytes( readBuffer );
        }
    }

    public ChannelBufferFactory factory()
    {
        return buffer.factory();
    }

    /**
     * Will return the capacity of the current chunk only
     */
    public int capacity()
    {
        return buffer.capacity();
    }

    public ByteOrder order()
    {
        return buffer.order();
    }

    public boolean isDirect()
    {
        return buffer.isDirect();
    }

    public int readerIndex()
    {
        return buffer.readerIndex();
    }

    public void readerIndex( int readerIndex )
    {
        buffer.readerIndex( readerIndex );
    }

    public int writerIndex()
    {
        return buffer.writerIndex();
    }

    public void writerIndex( int writerIndex )
    {
        buffer.writerIndex( writerIndex );
    }

    public void setIndex( int readerIndex, int writerIndex )
    {
        buffer.setIndex( readerIndex, writerIndex );
    }

    /**
     * Will return amount of readable bytes in this chunk only
     */
    public int readableBytes()
    {
        return buffer.readableBytes();
    }

    public int writableBytes()
    {
        return 0;
    }

    /**
     * Can fetch the next chunk if needed
     */
    public boolean readable()
    {
        readNextChunkIfNeeded( 1 );
        return buffer.readable();
    }

    public boolean writable()
    {
        return buffer.writable();
    }

    public void clear()
    {
        buffer.clear();
    }

    public void markReaderIndex()
    {
        buffer.markReaderIndex();
        hasMarkedReaderIndex = true;
    }

    public void resetReaderIndex()
    {
        buffer.resetReaderIndex();
        hasMarkedReaderIndex = false;
    }

    public void markWriterIndex()
    {
        buffer.markWriterIndex();
    }

    public void resetWriterIndex()
    {
        buffer.resetWriterIndex();
    }

    public void discardReadBytes()
    {
        int oldReaderIndex = buffer.readerIndex();
        if ( hasMarkedReaderIndex )
        {
            buffer.resetReaderIndex();
        }
        int bytesToDiscard = buffer.readerIndex();
        buffer.discardReadBytes();
        if ( hasMarkedReaderIndex )
        {
            buffer.readerIndex( oldReaderIndex-bytesToDiscard );
        }
    }

    public void ensureWritableBytes( int writableBytes )
    {
        buffer.ensureWritableBytes( writableBytes );
    }

    public byte getByte( int index )
    {
        readNextChunkIfNeeded( 1 );
        return buffer.getByte( index );
    }

    public short getUnsignedByte( int index )
    {
        readNextChunkIfNeeded( 1 );
        return buffer.getUnsignedByte( index );
    }

    public short getShort( int index )
    {
        readNextChunkIfNeeded( 2 );
        return buffer.getShort( index );
    }

    public int getUnsignedShort( int index )
    {
        readNextChunkIfNeeded( 2 );
        return buffer.getUnsignedShort( index );
    }

    public int getMedium( int index )
    {
        readNextChunkIfNeeded( 4 );
        return buffer.getMedium( index );
    }

    public int getUnsignedMedium( int index )
    {
        readNextChunkIfNeeded( 4 );
        return buffer.getUnsignedMedium( index );
    }

    public int getInt( int index )
    {
        readNextChunkIfNeeded( 4 );
        return buffer.getInt( index );
    }

    public long getUnsignedInt( int index )
    {
        readNextChunkIfNeeded( 4 );
        return buffer.getUnsignedInt( index );
    }

    public long getLong( int index )
    {
        readNextChunkIfNeeded( 8 );
        return buffer.getLong( index );
    }

    public char getChar( int index )
    {
        readNextChunkIfNeeded( 2 );
        return buffer.getChar( index );
    }

    public float getFloat( int index )
    {
        readNextChunkIfNeeded( 8 );
        return buffer.getFloat( index );
    }

    public double getDouble( int index )
    {
        readNextChunkIfNeeded( 8 );
        return buffer.getDouble( index );
    }

    public void getBytes( int index, ChannelBuffer dst )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( dst.writableBytes() );
        buffer.getBytes( index, dst );
    }

    public void getBytes( int index, ChannelBuffer dst, int length )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( length );
        buffer.getBytes( index, dst, length );
    }

    public void getBytes( int index, ChannelBuffer dst, int dstIndex, int length )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( length );
        buffer.getBytes( index, dst, dstIndex, length );
    }

    public void getBytes( int index, byte[] dst )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( dst.length );
        buffer.getBytes( index, dst );
    }

    public void getBytes( int index, byte[] dst, int dstIndex, int length )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( length );
        buffer.getBytes( index, dst, dstIndex, length );
    }

    public void getBytes( int index, ByteBuffer dst )
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( dst.limit() );
        buffer.getBytes( index, dst );
    }

    public void getBytes( int index, OutputStream out, int length ) throws IOException
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( length );
        buffer.getBytes( index, out, length );
    }

    public int getBytes( int index, GatheringByteChannel out, int length ) throws IOException
    {
        // TODO We need a loop for this (if dst is bigger than chunk size)
        readNextChunkIfNeeded( length );
        return buffer.getBytes( index, out, length );
    }

    private UnsupportedOperationException unsupportedOperation()
    {
        return new UnsupportedOperationException( "Not supported in a DechunkingChannelBuffer, it's used merely for reading" );
    }
    
    public void setByte( int index, int value )
    {
        throw unsupportedOperation();
    }

    public void setShort( int index, int value )
    {
        throw unsupportedOperation();
    }

    public void setMedium( int index, int value )
    {
        throw unsupportedOperation();
    }

    public void setInt( int index, int value )
    {
        throw unsupportedOperation();
    }

    public void setLong( int index, long value )
    {
        throw unsupportedOperation();
    }

    public void setChar( int index, int value )
    {
        throw unsupportedOperation();
    }

    public void setFloat( int index, float value )
    {
        throw unsupportedOperation();
    }

    public void setDouble( int index, double value )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, ChannelBuffer src )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, ChannelBuffer src, int length )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, ChannelBuffer src, int srcIndex, int length )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, byte[] src )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, byte[] src, int srcIndex, int length )
    {
        throw unsupportedOperation();
    }

    public void setBytes( int index, ByteBuffer src )
    {
        throw unsupportedOperation();
    }

    public int setBytes( int index, InputStream in, int length ) throws IOException
    {
        throw unsupportedOperation();
    }

    public int setBytes( int index, ScatteringByteChannel in, int length ) throws IOException
    {
        throw unsupportedOperation();
    }

    public void setZero( int index, int length )
    {
        throw unsupportedOperation();
    }

    public byte readByte()
    {
        readNextChunkIfNeeded( 1 );
        return buffer.readByte();
    }

    public short readUnsignedByte()
    {
        readNextChunkIfNeeded( 1 );
        return buffer.readUnsignedByte();
    }

    public short readShort()
    {
        readNextChunkIfNeeded( 2 );
        return buffer.readShort();
    }

    public int readUnsignedShort()
    {
        readNextChunkIfNeeded( 2 );
        return buffer.readUnsignedShort();
    }

    public int readMedium()
    {
        readNextChunkIfNeeded( 4 );
        return buffer.readMedium();
    }

    public int readUnsignedMedium()
    {
        readNextChunkIfNeeded( 4 );
        return buffer.readUnsignedMedium();
    }

    public int readInt()
    {
        readNextChunkIfNeeded( 4 );
        return buffer.readInt();
    }

    public long readUnsignedInt()
    {
        readNextChunkIfNeeded( 4 );
        return buffer.readUnsignedInt();
    }

    public long readLong()
    {
        readNextChunkIfNeeded( 8 );
        return buffer.readLong();
    }

    public char readChar()
    {
        readNextChunkIfNeeded( 2 );
        return buffer.readChar();
    }

    public float readFloat()
    {
        readNextChunkIfNeeded( 8 );
        return buffer.readFloat();
    }

    public double readDouble()
    {
        readNextChunkIfNeeded( 8 );
        return buffer.readDouble();
    }

    public ChannelBuffer readBytes( int length )
    {
        readNextChunkIfNeeded( length );
        return buffer.readBytes( length );
    }

    public ChannelBuffer readBytes( ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer readSlice( int length )
    {
        readNextChunkIfNeeded( length );
        return buffer.readSlice( length );
    }

    public ChannelBuffer readSlice( ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public void readBytes( ChannelBuffer dst )
    {
        readNextChunkIfNeeded( dst.writableBytes() );
        buffer.readBytes( dst );
    }

    public void readBytes( ChannelBuffer dst, int length )
    {
        readNextChunkIfNeeded( length );
        buffer.readBytes( dst, length );
    }

    public void readBytes( ChannelBuffer dst, int dstIndex, int length )
    {
        readNextChunkIfNeeded( length );
        buffer.readBytes( dst, dstIndex, length );
    }

    public void readBytes( byte[] dst )
    {
        readNextChunkIfNeeded( dst.length );
        buffer.readBytes( dst );
    }

    public void readBytes( byte[] dst, int dstIndex, int length )
    {
        readNextChunkIfNeeded( length );
        buffer.readBytes( dst, dstIndex, length );
    }

    public void readBytes( ByteBuffer dst )
    {
        readNextChunkIfNeeded( dst.limit() );
        buffer.readBytes( dst );
    }

    public void readBytes( OutputStream out, int length ) throws IOException
    {
        readNextChunkIfNeeded( length );
        buffer.readBytes( out, length );
    }

    public int readBytes( GatheringByteChannel out, int length ) throws IOException
    {
        readNextChunkIfNeeded( length );
        return buffer.readBytes( out, length );
    }

    public void skipBytes( int length )
    {
        readNextChunkIfNeeded( length );
        buffer.skipBytes( length );
    }

    public int skipBytes( ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public void writeByte( int value )
    {
        throw unsupportedOperation();
    }

    public void writeShort( int value )
    {
        throw unsupportedOperation();
    }

    public void writeMedium( int value )
    {
        throw unsupportedOperation();
    }

    public void writeInt( int value )
    {
        throw unsupportedOperation();
    }

    public void writeLong( long value )
    {
        throw unsupportedOperation();
    }

    public void writeChar( int value )
    {
        throw unsupportedOperation();
    }

    public void writeFloat( float value )
    {
        throw unsupportedOperation();
    }

    public void writeDouble( double value )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( ChannelBuffer src )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( ChannelBuffer src, int length )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( ChannelBuffer src, int srcIndex, int length )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( byte[] src )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( byte[] src, int srcIndex, int length )
    {
        throw unsupportedOperation();
    }

    public void writeBytes( ByteBuffer src )
    {
        throw unsupportedOperation();
    }

    public int writeBytes( InputStream in, int length ) throws IOException
    {
        throw unsupportedOperation();
    }

    public int writeBytes( ScatteringByteChannel in, int length ) throws IOException
    {
        throw unsupportedOperation();
    }

    public void writeZero( int length )
    {
        throw unsupportedOperation();
    }

    public int indexOf( int fromIndex, int toIndex, byte value )
    {
        throw unsupportedOperation();
    }

    public int indexOf( int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( byte value )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( int length, byte value )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( int length, ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( int index, int length, byte value )
    {
        throw unsupportedOperation();
    }

    public int bytesBefore( int index, int length, ChannelBufferIndexFinder indexFinder )
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer copy()
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer copy( int index, int length )
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer slice()
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer slice( int index, int length )
    {
        throw unsupportedOperation();
    }

    public ChannelBuffer duplicate()
    {
        throw unsupportedOperation();
    }

    public ByteBuffer toByteBuffer()
    {
        throw unsupportedOperation();
    }

    public ByteBuffer toByteBuffer( int index, int length )
    {
        throw unsupportedOperation();
    }

    public ByteBuffer[] toByteBuffers()
    {
        throw unsupportedOperation();
    }

    public ByteBuffer[] toByteBuffers( int index, int length )
    {
        throw unsupportedOperation();
    }

    public boolean hasArray()
    {
        throw unsupportedOperation();
    }

    public byte[] array()
    {
        throw unsupportedOperation();
    }

    public int arrayOffset()
    {
        throw unsupportedOperation();
    }

    public String toString( Charset charset )
    {
        return buffer.toString( charset );
    }

    public String toString( int index, int length, Charset charset )
    {
        return buffer.toString( index, length, charset );
    }

    public String toString( String charsetName )
    {
        return buffer.toString( charsetName );
    }

    public String toString( String charsetName, ChannelBufferIndexFinder terminatorFinder )
    {
        return buffer.toString( charsetName, terminatorFinder );
    }

    public String toString( int index, int length, String charsetName )
    {
        return buffer.toString( index, length, charsetName );
    }

    public String toString( int index, int length, String charsetName,
            ChannelBufferIndexFinder terminatorFinder )
    {
        return buffer.toString( index, length, charsetName, terminatorFinder );
    }

    public int hashCode()
    {
        return buffer.hashCode();
    }

    public boolean equals( Object obj )
    {
        return buffer.equals( obj );
    }

    public int compareTo( ChannelBuffer buffer )
    {
        return buffer.compareTo( buffer );
    }

    public String toString()
    {
        return buffer.toString();
    }
}
