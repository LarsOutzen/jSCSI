package org.jscsi.scsi.tasks.buffered;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;
import org.jscsi.scsi.protocol.Command;
import org.jscsi.scsi.protocol.cdb.TransferCDB;
import org.jscsi.scsi.protocol.cdb.Write6;
import org.jscsi.scsi.protocol.inquiry.InquiryDataRegistry;
import org.jscsi.scsi.protocol.mode.ModePageRegistry;
import org.jscsi.scsi.protocol.sense.exceptions.LogicalBlockAddressOutOfRangeException;
import org.jscsi.scsi.protocol.sense.exceptions.SenseException;
import org.jscsi.scsi.protocol.sense.exceptions.SynchronousDataTransferErrorException;
import org.jscsi.scsi.tasks.Status;
import org.jscsi.scsi.transport.TargetTransportPort;

public class BufferedWriteTask extends BufferedTask
{
   private static Logger _logger = Logger.getLogger(BufferedWriteTask.class);

   public BufferedWriteTask()
   {
      super();
   }

   @Override
   protected void execute(ByteBuffer store,
                          int blockSize,
                          TargetTransportPort targetPort,
                          Command command,
                          ModePageRegistry modePageRegistry,
                          InquiryDataRegistry inquiryDataRegistry)
   throws InterruptedException, SenseException
   {
      long capacity = this.getDeviceCapacity();

      TransferCDB cdb = (TransferCDB)command.getCommandDescriptorBlock();
      long lba = cdb.getLogicalBlockAddress();
      long transferLength = cdb.getTransferLength();

      // check if transfer would exceed the device size
      if ( lba < 0 || transferLength < 0 || lba > capacity || (lba + transferLength) > capacity)
      {
         switch (cdb.getOperationCode())
         {
            case Write6.OPERATION_CODE:
               throw new LogicalBlockAddressOutOfRangeException(true, true, (byte)4, 1);
            default:
               throw new LogicalBlockAddressOutOfRangeException(true, true, 2);
         }
      }

      // duplicate file byte buffer to avoid interference with other tasks
      store = store.duplicate();

      store.position( (int)(lba * blockSize) );

      // attempt to read data from transport port
      if ( ! this.readData(store) )
      {
         throw new SynchronousDataTransferErrorException();
      }

      // write operation complete
      this.writeResponse(Status.GOOD, null);
   }
}