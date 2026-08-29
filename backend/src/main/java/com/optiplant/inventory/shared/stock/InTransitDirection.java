package com.optiplant.inventory.shared.stock;

/** Whether an {@link InTransitShiftCommand} increments or decrements a branch's {@code in_transit_stock}. */
public enum InTransitDirection {
	INCREMENT,
	DECREMENT
}
