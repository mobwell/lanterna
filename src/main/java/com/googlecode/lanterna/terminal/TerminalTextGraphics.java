/*
 * This file is part of lanterna (http://code.google.com/p/lanterna/).
 *
 * lanterna is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2010-2014 Martin
 */
package com.googlecode.lanterna.terminal;

import com.googlecode.lanterna.common.AbstractTextGraphics;
import com.googlecode.lanterna.common.TextCharacter;
import com.googlecode.lanterna.common.TextGraphics;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is the Terminal's implementation of TextGraphics. Upon creation it takes a snapshot for the Terminal's size, so
 * that it won't require to do an expensive lookup on every call to {@code getSize()}, but this also means that it can
 * go stale quickly if the terminal is resized. You should try to use the object quickly and then let it be GC:ed. It
 * will not pick up on terminal resizes! Also, the state of the Terminal after an operation performed by this
 * TextGraphics implementation is undefined and you should probably re-initialize colors and modifiers.
 * <p/>
 * Any write operation that results in an IOException will be wrapped by a RuntimeException since the TextGraphics
 * interface doesn't allow throwing IOException
 */
class TerminalTextGraphics extends AbstractTextGraphics {

    private final Terminal terminal;
    private final TerminalSize terminalSize;

    private AtomicInteger manageCallStackSize;
    private TextCharacter lastCharacter;
    private TerminalPosition lastPosition;

    TerminalTextGraphics(Terminal terminal) throws IOException {
        this.terminal = terminal;
        this.terminalSize = terminal.getTerminalSize();
        this.manageCallStackSize = new AtomicInteger(0);
        this.lastCharacter = null;
        this.lastPosition = null;
    }

    @Override
    public TextGraphics setPosition(TerminalPosition newPosition) {
        super.setPosition(newPosition);
        try {
            terminal.setCursorPosition(newPosition.getColumn(), newPosition.getRow());
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    protected synchronized void setCharacter(int columnIndex, int rowIndex, TextCharacter textCharacter) {
        try {
            if(manageCallStackSize.get() > 0) {
                if(lastCharacter == null || !lastCharacter.equals(textCharacter)) {
                    applyGraphicState(textCharacter);
                    lastCharacter = textCharacter;
                }
                if(lastPosition == null || !lastPosition.equals(columnIndex, rowIndex)) {
                    terminal.setCursorPosition(columnIndex, rowIndex);
                    lastPosition = new TerminalPosition(columnIndex, rowIndex);
                }
            }
            else {
                terminal.setCursorPosition(columnIndex, rowIndex);
                applyGraphicState(textCharacter);
            }
            terminal.putCharacter(textCharacter.getCharacter());
            if(manageCallStackSize.get() > 0) {
                lastPosition = new TerminalPosition(columnIndex + 1, rowIndex);
            }
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyGraphicState(TextCharacter textCharacter) throws IOException {
        terminal.resetColorAndSGR();
        terminal.setForegroundColor(textCharacter.getForegroundColor());
        terminal.setBackgroundColor(textCharacter.getBackgroundColor());
        for(Terminal.SGR sgr: textCharacter.getModifiers()) {
            terminal.enableSGR(sgr);
        }
    }

    @Override
    public TerminalSize getSize() {
        return terminalSize;
    }

    @Override
    public synchronized void drawLine(TerminalPosition toPoint, char character) {
        try {
            enterAtomic();
            super.drawLine(toPoint, character);
        }
        finally {
            leaveAtomic();
        }
    }

    @Override
    public synchronized void drawTriangle(TerminalPosition p1, TerminalPosition p2, char character) {
        try {
            enterAtomic();
            super.drawTriangle(p1, p2, character);
        }
        finally {
            leaveAtomic();
        }
    }

    @Override
    public synchronized void fillTriangle(TerminalPosition p1, TerminalPosition p2, char character) {
        try {
            enterAtomic();
            super.fillTriangle(p1, p2, character);
        }
        finally {
            leaveAtomic();
        }
    }

    @Override
    public synchronized void fillRectangle(TerminalSize size, char character) {
        try {
            enterAtomic();
            super.fillRectangle(size, character);
        }
        finally {
            leaveAtomic();
        }
    }

    @Override
    public synchronized void drawRectangle(TerminalSize size, char character) {
        try {
            enterAtomic();
            super.drawRectangle(size, character);
        }
        finally {
            leaveAtomic();
        }
    }

    @Override
    public synchronized TextGraphics putString(String string) {
        try {
            enterAtomic();
            return super.putString(string);
        }
        finally {
            leaveAtomic();
        }
    }

    /**
     * It's tricky with this implementation because we can't rely on any state in between two calls to setCharacter
     * since the caller might modify the terminal's state outside of this writer. However, many calls inside
     * TextGraphics will indeed make multiple calls in setCharacter where we know that the state won't change (actually,
     * we can't be 100% sure since the caller might create a separate thread and maliciously write directly to the
     * terminal while call one of the draw/fill/put methods in here). We could just set the state before writing every
     * single character but that would be inefficient. Rather, we keep a counter of if we are inside an 'atomic'
     * (meaning we know multiple calls to setCharacter will have the same state). Some drawing methods call other
     * drawing methods internally for their implementation so that's why this is implemented with an integer value
     * instead of a boolean; when the counter reaches zero we remove the memory of what state the terminal is in.
     */
    private void enterAtomic() {
        manageCallStackSize.incrementAndGet();
    }

    private void leaveAtomic() {
        if(manageCallStackSize.decrementAndGet() == 0) {
            lastPosition = null;
            lastCharacter = null;
        }
    }
}
