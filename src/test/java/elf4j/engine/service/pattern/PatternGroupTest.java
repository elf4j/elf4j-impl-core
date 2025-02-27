/*
 * MIT License
 *
 * Copyright (c) 2023 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package elf4j.engine.service.pattern;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import elf4j.engine.service.LogEvent;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatternGroupTest {
  @Nested
  class render {
    @Mock
    PatternElement mockPattern;

    @Mock
    PatternElement mockPattern2;

    @Mock
    LogEvent stubLogEvent;

    LogPattern patternGroupEntry;

    @Test
    void dispatchAll() {
      patternGroupEntry = new LogPattern(Arrays.asList(mockPattern2, mockPattern));
      StringBuilder stringBuilder = new StringBuilder();

      patternGroupEntry.render(stubLogEvent, stringBuilder);

      InOrder inOrder = inOrder(mockPattern, mockPattern2);
      then(mockPattern2).should(inOrder).render(stubLogEvent, stringBuilder);
      then(mockPattern).should(inOrder).render(stubLogEvent, stringBuilder);
    }
  }
}
