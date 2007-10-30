//
// Cleversafe open-source code header - Version 1.1 - December 1, 2006
//
// Cleversafe Dispersed Storage(TM) is software for secure, private and
// reliable storage of the world's data using information dispersal.
//
// Copyright (C) 2005-2007 Cleversafe, Inc.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
// USA.
//
// Contact Information: Cleversafe, 10 W. 35th Street, 16th Floor #84,
// Chicago IL 60616
// email licensing@cleversafe.org
//
// END-OF-HEADER
//-----------------------
// @author: mmotwani
//
// Date: Oct 26, 2007
//---------------------

package org.jscsi.scsi.protocol.cdb;

// TODO: Describe class or interface
public abstract class AbstractTransferCommandDescriptorBlock extends AbstractCommandDescriptorBlock
{
   private long transferLength;
   private long logicalBlockAddress;

   public AbstractTransferCommandDescriptorBlock(int operationCode)
   {
      super(operationCode);
   }

   public AbstractTransferCommandDescriptorBlock(
         int operationCode,
         boolean linked,
         boolean normalACA,
         long logicalBlockAddress,
         long transferLength)
   {
      super(operationCode, linked, normalACA);
      this.transferLength = transferLength;
      this.logicalBlockAddress = logicalBlockAddress;
   }

   public long getLogicalBlockAddress()
   {
      return this.logicalBlockAddress;
   }

   /**
    * The transfer length, usually in blocks. Zero if the command does not require a transfer length
    * or no data is to be transferred.
    * 
    * @return Transfer length in blocks.
    */
   public long getTransferLength()
   {
      return this.transferLength;
   }

   public void setLogicalBlockAddress(long logicalBlockAddress)
   {
      this.logicalBlockAddress = logicalBlockAddress;
   }

   public void setTransferLength(long transferLength)
   {
      this.transferLength = transferLength;
   }
}