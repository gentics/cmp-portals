<?php
namespace Gentics\PortalPhp;

use BenSampo\Enum\Enum;

/**
* Render modes of PortalPhp
*/
class RenderMode extends Enum
{
    /** Default mode, when the page is rendered in browser itself or publish mode from cms */
    const PUBLISH = 0;

    /** Portal rendered in CMS iframe, in preview mode */
    const PREVIEW = 1;

    /** Portal rendered in CMS iframe, in edit mode */
    const EDIT = 2;

    /** Portal rendered in CMS iframe, in live preview mode */
    const LIVE = 3;
}
