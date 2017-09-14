export const environment = {
  production: true,
  api: function (resource: string) {
    const res = resource || '';
    return window.location.protocol + '//' + window.location.host + '/api' + (res.startsWith('/') ? res : '/' + res);
  }
};
