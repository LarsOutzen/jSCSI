/*
 * Copyright 2007 Marc Kramis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * $Id: Raid1Device.java 2642 2007-04-10 09:46:49Z lemke $
 * 
 */

package org.jscsi;

import java.util.Vector;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <h1>Raid0Device</h1>
 * 
 * <p>
 * Implements a RAID 0 Device with several Devices.
 * </p>
 * 
 * @author Bastian Lemke
 * 
 */
public class Raid0Device implements Device {

  private final Device[] devices;

  private int blockSize = -1;

  private long blockCount = -1;

  /** The Logger interface. */
  private static final Log LOGGER = LogFactory.getLog(Raid0Device.class);

  /**
   * Size of the parts, that are distributed between the Devices. Must be a
   * multiple of blockSize.
   */
  private static final int EXTEND_SIZE = 8192;

  /** Thread pool for write- and read-threads. */
  private final ExecutorService executor;

  /** Thread barrier for write- and read-threads. */
  private CyclicBarrier barrier;

  /**
   * Constructor to create an Raid0Device. The Device has to be initialized
   * before it can be used.
   * 
   * @param initDevices
   *          devices to use
   * 
   * @throws Exception
   *           if any error occurs
   */
  public Raid0Device(final Device[] initDevices) throws Exception {

    devices = initDevices;
    // create one thread per device
    executor = Executors.newFixedThreadPool(devices.length);
  }

  /** {@inheritDoc} */
  public void close() throws Exception {

    if (blockCount == -1) {
      throw new NullPointerException();
    }

    executor.shutdown();
    for (Device device : devices) {
      device.close();
    }
    blockSize = -1;
    blockCount = -1;
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Closed " + getName() + ".");
    }
  }

  /** {@inheritDoc} */
  public int getBlockSize() {

    if (blockSize == -1) {
      throw new IllegalStateException("You first have to open the Device!");
    }

    return blockSize;
  }

  /** {@inheritDoc} */
  public String getName() {

    String name = "Raid0Device(";
    for (Device device : devices) {
      name += device.getName() + "+";
    }
    return name.substring(0, name.length() - 1) + ")";
  }

  /** {@inheritDoc} */
  public long getBlockCount() {

    if (blockCount == -1) {
      throw new IllegalStateException("You first have to open the Device!");
    }

    return blockCount;
  }

  /** {@inheritDoc} */
  public void open() throws Exception {

    if (blockCount != -1) {
      throw new IllegalStateException("Raid0Device is already opened!");
    }

    for (Device device : devices) {
      device.open();
    }

    // check if all devices have the same block size
    blockSize = 0;
    for (Device device : devices) {
      if (blockSize == 0) {
        blockSize = (int) device.getBlockSize();
      } else if (blockSize != (int) device.getBlockSize()) {
        throw new IllegalArgumentException(
            "All devices must have the same block size!");
      }
    }

    if (EXTEND_SIZE % blockSize != 0) {
      throw new IllegalArgumentException(
          "EXTEND_SIZE must be a multiple of the blocksize!");
    }

    // available space = size of smallest device * #devices
    blockCount = Long.MAX_VALUE;
    for (Device device : devices) {
      blockCount = Math.min(blockCount, device.getBlockCount());
    }
    blockCount = (blockCount * blockSize / EXTEND_SIZE) * EXTEND_SIZE
        / blockSize;
    blockCount *= devices.length;
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Opened " + getName() + ".");
    }
  }

  /** {@inheritDoc} */
  public void read(final long address, final byte[] data) throws Exception {

    if (blockCount == -1) {
      throw new IllegalStateException("You first have to open the Device!");
    }

    if (data.length % EXTEND_SIZE != 0) {
      throw new IllegalArgumentException(
          "Number of bytes is not a multiple of the extend size ("
              + EXTEND_SIZE + ")!");
    }

    int fragments = data.length / EXTEND_SIZE;
    int blocks = data.length / blockSize;
    int blockFactor = EXTEND_SIZE / blockSize;

    if (address < 0 || address + blocks > blockCount) {
      long adr = address < 0 ? address : address + blocks - 1;
      throw new IllegalArgumentException("Address " + adr + " out of range.");
    }

    int parts = (fragments >= devices.length) ? devices.length : fragments;
    barrier = new CyclicBarrier(parts + 1);
    long actualAddress = ((address / blockFactor) / devices.length)
        * blockFactor;
    int actualDevice = (int) ((address / blockFactor) % devices.length);
    Vector<byte[]> deviceData = new Vector<byte[]>();
    int deviceBlockCount;
    for (int i = 0; i < parts; i++) {
      deviceBlockCount = fragments / devices.length * blockFactor;
      if (i < (fragments % devices.length)) {
        deviceBlockCount += blockFactor;
      }
      deviceData.add(new byte[deviceBlockCount * blockSize]);

      if (deviceBlockCount != 0) {
        executor.execute(new ReadThread(devices[actualDevice], actualAddress,
            deviceData.get(i)));
      }
      if (actualDevice == devices.length - 1) {
        actualDevice = 0;
        actualAddress += blockFactor;
      } else {
        actualDevice++;
      }
    }
    barrier.await();

    /** Merge the results. */
    for (int i = 0; i < fragments; i++) {
      System.arraycopy(deviceData.get(i % devices.length), i / devices.length
          * EXTEND_SIZE, data, i * EXTEND_SIZE, EXTEND_SIZE);
    }
  }

  /** {@inheritDoc} */
  public void write(final long address, final byte[] data) throws Exception {

    if (blockCount == -1) {
      throw new IllegalStateException("You first have to open the Device!");
    }

    int fragments = data.length / EXTEND_SIZE;
    int blocks = data.length / blockSize;
    int blockFactor = EXTEND_SIZE / blockSize;

    if (address < 0 || address + blocks > blockCount) {
      long adr = address < 0 ? address : address + blocks - 1;
      throw new IllegalArgumentException("Address " + adr + " out of range.");
    }

    if (data.length % EXTEND_SIZE != 0) {
      throw new IllegalArgumentException(
          "Number of bytes is not a multiple of the extend size ("
              + EXTEND_SIZE + ")!");
    }

    int parts = (fragments >= devices.length) ? devices.length : fragments;
    Vector<byte[]> deviceData = new Vector<byte[]>();
    long actualAddress = ((address / blockFactor) / devices.length)
        * blockFactor;
    int actualDevice = (int) ((address / blockFactor) % devices.length);
    int deviceBlockCount;
    barrier = new CyclicBarrier(parts + 1);
    for (int i = 0; i < parts; i++) {
      deviceBlockCount = fragments / devices.length * blockFactor;
      if (i < (fragments % devices.length)) {
        deviceBlockCount += blockFactor;
      }
      deviceData.add(new byte[deviceBlockCount * blockSize]);
    }
    for (int i = 0; i < fragments; i++) {
      System.arraycopy(data, i * EXTEND_SIZE, deviceData
          .get(i % devices.length), i / devices.length * EXTEND_SIZE,
          EXTEND_SIZE);
    }
    for (int i = 0; i < parts; i++) {
      executor.execute(new WriteThread(devices[actualDevice], actualAddress,
          deviceData.get(i)));
      if (actualDevice == devices.length - 1) {
        actualDevice = 0;
        actualAddress += blockFactor;
      } else {
        actualDevice++;
      }
    }
    barrier.await();
  }

  /**
   * Private class to represent one single read-action in an own thread for one
   * target.
   * 
   * @author bastian
   * 
   */
  private final class ReadThread implements Runnable {

    private final Device device;

    private final long address;

    private final byte[] data;

    private ReadThread(final Device readDevice, final long readBlockAddress,
        final byte[] readData) {

      device = readDevice;
      address = readBlockAddress;
      data = readData;
    }

    public void run() {

      try {
        device.read(address, data);
        if (LOGGER.isDebugEnabled()) {
          LOGGER
              .debug("Read " + data.length / blockSize
                  + " blocks from address " + address + " from "
                  + device.getName());
        }
        barrier.await();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Private class to represent one single write-action in an own thread for one
   * target.
   * 
   * @author Bastian Lemke
   * 
   */
  private final class WriteThread implements Runnable {

    private final Device device;

    private final long address;

    private final byte[] data;

    private WriteThread(final Device writeDevice, final long writeBlockAddress,
        final byte[] writeData) {

      device = writeDevice;
      address = writeBlockAddress;
      data = writeData;
    }

    public void run() {

      try {
        device.write(address, data);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Wrote " + data.length / blockSize
              + " blocks to address " + address + " to " + device.getName());
        }
        barrier.await();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }
}
