export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'header-max-length': [2, 'always', 72],
    'subject-case': [2, 'always', 'lower-case'],
    'scope-enum': [2, 'always', ['shared', 'backend', 'worker', 'frontend', 'infra', 'ci', 'docs']],
  },
};
