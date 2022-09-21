/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getExecutionDuration} from './getExecutionDuration';

const MOCK_EXECUTION_DATE = '21 seconds';
const MOCK_START_DATE = '2022-01-01T11:00:00.000+0000';
const MOCK_END_DATE = '2022-01-03T11:00:00.000+0000';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
}));

describe('getExecutionDuration', () => {
  it('should return a duration for open periods', () => {
    expect(getExecutionDuration(MOCK_START_DATE, null)).toBe(
      `${MOCK_EXECUTION_DATE} (running)`
    );
  });

  it('should return a duration for periods of less than 1 second', () => {
    expect(getExecutionDuration(MOCK_START_DATE, MOCK_START_DATE)).toBe(
      'Less than 1 second'
    );
  });

  it('should return a duration for all other closed periods', () => {
    expect(getExecutionDuration(MOCK_START_DATE, MOCK_END_DATE)).toBe('2 days');
  });
});
