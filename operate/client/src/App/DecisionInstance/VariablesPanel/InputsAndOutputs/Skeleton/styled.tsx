/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText as BaseSkeletonText} from '@carbon/react';
import styled from 'styled-components';

const Container = styled.div`
  position: relative;
  height: 100%;
`;

const Ul = styled.ul`
  overflow: hidden;
  width: 100%;
  position: absolute;
`;

const Li = styled.li`
  margin: var(--cds-spacing-05);
  display: flex;
  gap: var(--cds-spacing-05);
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

export {Container, Ul, Li, SkeletonText};
