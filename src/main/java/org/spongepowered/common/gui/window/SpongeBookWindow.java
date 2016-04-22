/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.gui.window;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.gui.window.BookWindow;
import org.spongepowered.api.text.BookView;
import org.spongepowered.common.util.BookFaker;

public class SpongeBookWindow extends AbstractSpongeWindow implements BookWindow {

    private static final BookView EMPTY_BOOK = BookView.builder().build();

    private BookView bookView;

    @Override
    protected boolean show() {
        BookFaker.fakeBookView(getBookView(), (Player) this.player);
        return true;
    }

    @Override
    public BookView getBookView() {
        return this.bookView != null ? this.bookView : EMPTY_BOOK;
    }

    @Override
    public void setBookView(BookView view) {
        this.bookView = view;
    }

    public static class Builder extends SpongeWindowBuilder<BookWindow, BookWindow.Builder> implements BookWindow.Builder {

        @Override
        public BookWindow build() {
            return new SpongeBookWindow();
        }
    }

}