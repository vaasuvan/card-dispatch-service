module.exports = (output, context) => {
    const expectedStatus = context.config.expectedStatus;
    if (output?.httpStatus !== expectedStatus) {
        return false;
    }

    if (expectedStatus === 404) {
        return output.body?.code === "DISPATCH_NOT_FOUND";
    }

    const body = output.body;
    const validStatuses = [
        "REGISTERED",
        "DISPATCHED",
        "IN_TRANSIT",
        "OUT_FOR_DELIVERY",
        "DELIVERY_ATTEMPTED",
        "DELIVERED",
        "RETURNED",
        "CANCELED",
        "EXCEPTION",
        "UNKNOWN",
    ];

    return body?.dispatchId === context.vars.dispatchId
        && validStatuses.includes(body?.status)
        && typeof body?.lastUpdatedAt === "string"
        && Array.isArray(body?.events)
        && body.events.every((event) => validStatuses.includes(event.status));
};
