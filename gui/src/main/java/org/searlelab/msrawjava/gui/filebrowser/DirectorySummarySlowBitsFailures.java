package org.searlelab.msrawjava.gui.filebrowser;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

final class DirectorySummarySlowBitsFailures {
	static final String SLOW_BITS_CANCELLED_BY_USER_SUMMARY="previous request was cancelled by user";

	private DirectorySummarySlowBitsFailures() {
	}

	static String expectedSlowBitsFailureSummary(Throwable failure) {
		if (failure==null) return null;
		if (Thread.currentThread().isInterrupted()) {
			return SLOW_BITS_CANCELLED_BY_USER_SUMMARY;
		}
		if (hasCancelledGrpcStatus(failure)) {
			return SLOW_BITS_CANCELLED_BY_USER_SUMMARY;
		}
		String lower=exceptionChainMessage(failure);
		if (lower.contains("cancelled: thread interrupted")||lower.contains("statusruntimeexception:cancelled")
				||lower.contains("statusruntimeexception: cancelled")||hasInterruptedCause(failure)) {
			return SLOW_BITS_CANCELLED_BY_USER_SUMMARY;
		}
		if (lower.contains("no valid type found for")) {
			return "input is not a supported TIMS .d dataset";
		}
		if (lower.contains("instrument index")) {
			return "Thermo RAW has no usable MS instrument index";
		}
		if (lower.contains("no such table: precursor")) {
			return "older DIA schema is missing precursor table";
		}
		if (lower.contains("no such table: fractions")) {
			return "older DIA schema is missing fractions table";
		}
		return null;
	}

	private static String exceptionChainMessage(Throwable throwable) {
		StringBuilder sb=new StringBuilder();
		Throwable cur=throwable;
		while (cur!=null) {
			sb.append(cur.getClass().getName()).append(':');
			String msg=cur.getMessage();
			if (msg!=null) {
				sb.append(msg);
			}
			sb.append('\n');
			Throwable next=cur.getCause();
			if (next==cur) {
				break;
			}
			cur=next;
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	private static boolean hasInterruptedCause(Throwable throwable) {
		Throwable cur=throwable;
		while (cur!=null) {
			if (cur instanceof InterruptedException) {
				return true;
			}
			Throwable next=cur.getCause();
			if (next==cur) {
				break;
			}
			cur=next;
		}
		return false;
	}

	private static boolean hasCancelledGrpcStatus(Throwable throwable) {
		Throwable cur=throwable;
		while (cur!=null) {
			if (cur instanceof StatusRuntimeException) {
				StatusRuntimeException sre=(StatusRuntimeException)cur;
				Status status=sre.getStatus();
				if (status!=null&&status.getCode()==Status.Code.CANCELLED) {
					return true;
				}
			}
			if (cur instanceof StatusException) {
				StatusException se=(StatusException)cur;
				Status status=se.getStatus();
				if (status!=null&&status.getCode()==Status.Code.CANCELLED) {
					return true;
				}
			}
			Throwable next=cur.getCause();
			if (next==cur) {
				break;
			}
			cur=next;
		}
		return false;
	}

	static boolean isThermoReaderUnavailable(Throwable throwable) {
		Throwable cur=throwable;
		while (cur!=null) {
			if (cur instanceof StatusRuntimeException) {
				StatusRuntimeException sre=(StatusRuntimeException)cur;
				Status status=sre.getStatus();
				if (status!=null&&status.getCode()==Status.Code.UNAVAILABLE) {
					return true;
				}
			}
			if (cur instanceof StatusException) {
				StatusException se=(StatusException)cur;
				Status status=se.getStatus();
				if (status!=null&&status.getCode()==Status.Code.UNAVAILABLE) {
					return true;
				}
			}
			String msg=cur.getMessage();
			if (msg!=null) {
				String lower=msg.toLowerCase(Locale.ROOT);
				if (lower.contains("connection refused")||lower.contains("failed to connect")) {
					return true;
				}
			}
			Throwable next=cur.getCause();
			if (next==cur) {
				break;
			}
			cur=next;
		}
		return false;
	}

	static void shutdownSlowBitsPool(ExecutorService pool) {
		if (pool==null) return;
		// Let in-flight readers finish so panel teardown does not interrupt gRPC calls mid-request.
		pool.shutdown();
	}

	static boolean shouldSkipThermoRetryOnClose(boolean closed) {
		return closed;
	}
}
