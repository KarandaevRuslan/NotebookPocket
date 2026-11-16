// Command palette only needs safe preferences JSON decoding from Acode helpers.
export default {
  parseJSON(text) {
    try {
      return JSON.parse(text);
    } catch {
      return null;
    }
  }
};
