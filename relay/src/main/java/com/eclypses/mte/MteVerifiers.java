// Copyright (c) Eclypses, Inc.
// 
// All rights reserved.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
// CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
// TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
// SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

// WARNING: This file is automatically generated. Do not edit.

package com.eclypses.mte;

import java.util.Map;
import java.util.HashMap;

// Message verifiers.
public enum MteVerifiers
{
  // None.
  mte_verifiers_none,

  // CRC-32 checksum of data.
  mte_verifiers_crc32,

  // CRC-32 checksum of data and sequencing.
  mte_verifiers_crc32_seq,

  // Sequencing.
  mte_verifiers_seq,

  // 64-bit timestamp.
  mte_verifiers_t64,

  // 64-bit timestamp and CRC-32 checksum of data and timestamp.
  mte_verifiers_t64_crc32,

  // 64-bit timestamp, CRC-32 checksum of data and timestamp, and
  // sequencing.
  mte_verifiers_t64_crc32_seq,

  // 64-bit timestamp and sequencing.
  mte_verifiers_t64_seq;

  // Enum <-> Integer conversion.
  public int getValue() { return ordinal(); }
  public static MteVerifiers valueOf(int i) { return map.get(i); }
  private static final Map<Integer, MteVerifiers> map =
    new HashMap<>();
  static
  {
    for (MteVerifiers e : MteVerifiers.values())
    {
      map.put(e.getValue(), e);
    }
  }
} 




