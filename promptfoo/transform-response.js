module.exports = (json, text, context) => ({
    httpStatus: context?.response?.status,
    body: json ?? text,
});
