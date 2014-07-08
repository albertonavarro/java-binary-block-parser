/* 
 * Copyright 2014 Igor Maznitsa (http://www.igormaznitsa.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.jbbp.io;

/**
 * Constants for bit order of reading operations.
 */
public enum JBBPBitOrder {
  /**
   * Most Significant Bit First means that the most significant bit will arrive first, the 7th bit will be read as the first one
   */
  MSB0,
  /**
   * Least Significant Bit First means that the least significant bit will arrive first, the 0th bit will be read as the first one
   */
  LSB0
}